package com.sencha.sencha.android.llama

class LlamaBridge {
    companion object {
        init {
            System.loadLibrary("sencha_llama")
        }
    }

    external fun createSession(): Long

    external fun destroySession(handle: Long)

    external fun loadModel(handle: Long, path: String, contextLength: Int): Boolean

    external fun generate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback,
    ): Boolean

    external fun cancel(handle: Long)

    external fun lastError(handle: Long): String?

    interface TokenCallback {
        fun onToken(text: String, isFinal: Boolean): Boolean
    }
}
