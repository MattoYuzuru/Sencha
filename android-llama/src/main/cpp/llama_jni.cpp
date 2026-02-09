#include <android/log.h>
#include <jni.h>
#include <algorithm>
#include <atomic>
#include <mutex>
#include <string>
#include <vector>
#include <unistd.h>
#include "llama.h"

namespace {
constexpr int kMinThreads = 2;
constexpr int kMaxThreads = 4;
constexpr int kThreadHeadroom = 2;
constexpr int kMaxBatchSize = 512;
constexpr float kDefaultTemp = 0.7f;

struct LlamaSession {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    std::atomic<bool> cancel_requested{false};
    std::string last_error;
    int32_t n_ctx = 0;
};

std::once_flag g_backend_once;

void log_callback(enum ggml_log_level level, const char * text, void *) {
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: prio = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN: prio = ANDROID_LOG_WARN; break;
        case GGML_LOG_LEVEL_INFO: prio = ANDROID_LOG_INFO; break;
        case GGML_LOG_LEVEL_DEBUG: prio = ANDROID_LOG_DEBUG; break;
        default: prio = ANDROID_LOG_INFO; break;
    }
    __android_log_print(prio, "sencha-llama", "%s", text);
}

void ensure_backend_init() {
    std::call_once(g_backend_once, []() {
        llama_log_set(log_callback, nullptr);
        llama_backend_init();
    });
}

int compute_threads() {
    long cores = sysconf(_SC_NPROCESSORS_ONLN);
    if (cores <= 0) {
        return kMinThreads;
    }
    int threads = static_cast<int>(cores) - kThreadHeadroom;
    if (threads < kMinThreads) threads = kMinThreads;
    if (threads > kMaxThreads) threads = kMaxThreads;
    return threads;
}

bool abort_callback(void * data) {
    auto * session = static_cast<LlamaSession *>(data);
    return session != nullptr && session->cancel_requested.load();
}

LlamaSession * session_from_handle(jlong handle) {
    return reinterpret_cast<LlamaSession *>(handle);
}

void set_error(LlamaSession * session, const std::string & error) {
    if (session) {
        session->last_error = error;
    }
}

void clear_session(LlamaSession * session) {
    if (!session) return;
    if (session->ctx) {
        llama_free(session->ctx);
        session->ctx = nullptr;
    }
    if (session->model) {
        llama_model_free(session->model);
        session->model = nullptr;
    }
    session->cancel_requested.store(false);
    session->n_ctx = 0;
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_createSession(
    JNIEnv * /* env */,
    jobject /* this */
) {
    ensure_backend_init();
    auto * session = new LlamaSession();
    return reinterpret_cast<jlong>(session);
}

extern "C" JNIEXPORT void JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_destroySession(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle
) {
    auto * session = session_from_handle(handle);
    if (!session) return;
    clear_session(session);
    delete session;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_loadModel(
    JNIEnv * env,
    jobject /* this */,
    jlong handle,
    jstring path,
    jint contextLength
) {
    auto * session = session_from_handle(handle);
    if (!session) return JNI_FALSE;
    ensure_backend_init();
    clear_session(session);
    session->last_error.clear();

    const char * path_chars = env->GetStringUTFChars(path, nullptr);
    if (!path_chars) {
        set_error(session, "Invalid model path");
        return JNI_FALSE;
    }
    std::string model_path(path_chars);
    env->ReleaseStringUTFChars(path, path_chars);

    llama_model_params model_params = llama_model_default_params();
    llama_model * model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (!model) {
        set_error(session, "Failed to load model file");
        return JNI_FALSE;
    }

    const int32_t trained_ctx = llama_model_n_ctx_train(model);
    int32_t requested_ctx = contextLength > 0 ? contextLength : trained_ctx;
    if (trained_ctx > 0 && requested_ctx > trained_ctx) {
        requested_ctx = trained_ctx;
    }
    if (requested_ctx <= 0) {
        requested_ctx = 2048;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(requested_ctx);
    ctx_params.n_batch = static_cast<uint32_t>(std::min(requested_ctx, kMaxBatchSize));
    ctx_params.n_ubatch = ctx_params.n_batch;
    const int threads = compute_threads();
    ctx_params.n_threads = threads;
    ctx_params.n_threads_batch = threads;
    ctx_params.abort_callback = abort_callback;
    ctx_params.abort_callback_data = session;

    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        set_error(session, "Failed to create inference context");
        llama_model_free(model);
        return JNI_FALSE;
    }

    session->model = model;
    session->ctx = ctx;
    session->n_ctx = static_cast<int32_t>(llama_n_ctx(ctx));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_generate(
    JNIEnv * env,
    jobject /* this */,
    jlong handle,
    jstring prompt,
    jint maxTokens,
    jfloat temperature,
    jobject callback
) {
    auto * session = session_from_handle(handle);
    if (!session || !session->model || !session->ctx) {
        set_error(session, "Model is not loaded");
        return JNI_FALSE;
    }
    session->last_error.clear();
    session->cancel_requested.store(false);

    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_chars) {
        set_error(session, "Invalid prompt");
        return JNI_FALSE;
    }
    std::string prompt_text(prompt_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    if (maxTokens <= 0) {
        set_error(session, "maxTokens must be positive");
        return JNI_FALSE;
    }

    const llama_vocab * vocab = llama_model_get_vocab(session->model);
    int n_prompt = llama_tokenize(vocab, prompt_text.c_str(), prompt_text.size(), nullptr, 0, true, true);
    if (n_prompt < 0) {
        n_prompt = -n_prompt;
    }
    if (n_prompt <= 0) {
        set_error(session, "Prompt is empty");
        return JNI_FALSE;
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_prompt));
    if (llama_tokenize(
            vocab,
            prompt_text.c_str(),
            prompt_text.size(),
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()),
            true,
            true) < 0) {
        set_error(session, "Failed to tokenize prompt");
        return JNI_FALSE;
    }

    int n_ctx = session->n_ctx > 0 ? session->n_ctx : static_cast<int>(llama_n_ctx(session->ctx));
    if (n_prompt + maxTokens >= n_ctx) {
        set_error(session, "Prompt is too long for the current context window");
        return JNI_FALSE;
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature > 0.0f ? temperature : kDefaultTemp));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(0));

    jclass callback_class = env->GetObjectClass(callback);
    jmethodID on_token = callback_class
        ? env->GetMethodID(callback_class, "onToken", "(Ljava/lang/String;Z)Z")
        : nullptr;
    if (!on_token) {
        set_error(session, "TokenCallback.onToken not found");
        if (callback_class) env->DeleteLocalRef(callback_class);
        llama_sampler_free(sampler);
        return JNI_FALSE;
    }

    llama_memory_clear(llama_get_memory(session->ctx), false);

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_model_has_encoder(session->model)) {
        if (llama_encode(session->ctx, batch) != 0) {
            set_error(session, "Failed to encode prompt");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }

        llama_token decoder_start = llama_model_decoder_start_token(session->model);
        if (decoder_start == LLAMA_TOKEN_NULL) {
            decoder_start = llama_vocab_bos(vocab);
        }
        batch = llama_batch_get_one(&decoder_start, 1);
    }

    for (int i = 0; i < maxTokens; ++i) {
        if (session->cancel_requested.load()) {
            set_error(session, "CANCELED");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }
        if (llama_decode(session->ctx, batch) != 0) {
            set_error(session, "llama_decode failed");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }

        llama_token token = llama_sampler_sample(sampler, session->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) {
            break;
        }

        char buf[1024];
        int n = llama_token_to_piece(vocab, token, buf, sizeof(buf), 0, true);
        if (n < 0) {
            set_error(session, "Failed to detokenize output");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }
        std::string piece(buf, static_cast<size_t>(n));
        jstring token_text = env->NewStringUTF(piece.c_str());
        jboolean keep = env->CallBooleanMethod(callback, on_token, token_text, JNI_FALSE);
        env->DeleteLocalRef(token_text);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            set_error(session, "Token callback threw");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }
        if (!keep) {
            set_error(session, "CANCELED");
            env->DeleteLocalRef(callback_class);
            llama_sampler_free(sampler);
            return JNI_FALSE;
        }

        batch = llama_batch_get_one(&token, 1);
    }

    env->DeleteLocalRef(callback_class);
    llama_sampler_free(sampler);
    session->last_error.clear();
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_cancel(
    JNIEnv * /* env */,
    jobject /* this */,
    jlong handle
) {
    auto * session = session_from_handle(handle);
    if (!session) return;
    session->cancel_requested.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_sencha_sencha_android_llama_LlamaBridge_lastError(
    JNIEnv * env,
    jobject /* this */,
    jlong handle
) {
    auto * session = session_from_handle(handle);
    if (!session || session->last_error.empty()) {
        return nullptr;
    }
    return env->NewStringUTF(session->last_error.c_str());
}
