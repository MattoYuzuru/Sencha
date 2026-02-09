# Sencha

Sencha — мультиплатформенный клиент (Android/iOS) для локальной и удаленной работы с AI‑моделями. Основной фокус:
offline‑first, job‑система, менеджер моделей.

## Быстрый старт

### Android

Сборка APK:

```shell
./gradlew :androidApp:assembleDebug
```

APK появится здесь: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

Запуск из Android Studio: конфигурация `androidApp` или:

```shell
./gradlew :androidApp:installDebug
```

### iOS

Откройте `iosApp/iosApp.xcodeproj` в Xcode и запустите таргет `iosApp`. Xcode сам вызовет Gradle и встроит
`Shared`‑фреймворк.

### Тесты

```shell
./gradlew test
```

## Sync server (Ktor)

### Локальный запуск

Требуются S3‑совместимое хранилище (например, MinIO) и переменные окружения:

```shell
export SENCHA_S3_ENDPOINT="http://localhost:9000"
export SENCHA_S3_REGION="us-east-1"
export SENCHA_S3_BUCKET="sencha"
export SENCHA_S3_ACCESS_KEY="minioadmin"
export SENCHA_S3_SECRET_KEY="minioadmin"
export SENCHA_S3_FORCE_PATH_STYLE="true"
export SENCHA_REGISTRATION_CODE="SENCHA-DEV-0001"
./gradlew :server:run
```

Сервер слушает `http://localhost:8080` по умолчанию. Для production требуется HTTPS.

### MinIO (пример)

```shell
docker run --name sencha-minio -p 9000:9000 -p 9001:9001 \\
  -e MINIO_ROOT_USER=minioadmin \\
  -e MINIO_ROOT_PASSWORD=minioadmin \\
  quay.io/minio/minio server /data --console-address :9001
```

Создайте bucket `sencha` в консоли MinIO (`http://localhost:9001`).

### Подключение приложения

В разделе **Связи**:
- укажите URL сервера (HTTPS в release),
- введите одноразовый код,
- нажмите **Подключить**.

После регистрации можно нажать **Синхронизировать** для ручного обновления.

### Tailnet onboarding (MVP)

Рекомендуем Tailscale или ZeroTier для подключения домашнего сервера без проброса портов:
- https://tailscale.com/kb/
- https://docs.zerotier.com/

### Линт

```shell
./gradlew ktlintCheck detekt
```

## Структура репозитория

- `androidApp/` — Android приложение (Compose).
- `iosApp/` — iOS приложение (UIKit).
- `shared/` — KMP общий код.
- `shared/core/model/` — capabilities и параметры моделей.
- `shared/core/domain/` — use‑cases.
- `shared/core/data/` — репозитории и хранилища.
- `shared/core/jobs/` — job engine.
- `shared/core/security/` — абстракции безопасности.

## Примечания

- `RemoteOllamaProvider` по умолчанию подключается к `http://localhost:11434`.
- Для удаленных хостов требуется HTTPS/TLS.

## Docs consulted

- Apple HIG “Materials” (Liquid Glass): использовать стекло в функциональном слое (nav/tab bars), не в контенте;
  соблюдать Reduce Transparency. Применено к навигации, таб‑бару и инпут‑бару, без многослойной прозрачности.
  https://developer.apple.com/tutorials/data/design/human-interface-guidelines/materials.json
- UIKit `UIVisualEffectView` / `UIBlurEffect`: размещать контент в `contentView`, не менять `alpha` эффекта.
  Выбрали Blur‑материалы вместо ручной полупрозрачности, чтобы сохранить читабельность.
  https://developer.apple.com/tutorials/data/documentation/uikit/uivisualeffectview.json
  https://developer.apple.com/tutorials/data/documentation/uikit/uiblureffect.json
- Kotlin Flow: стриминг и состояние через Flow/StateFlow вместо callback‑ов.
  https://kotlinlang.org/docs/flow.html
- Kotlin Serialization: сериализация моделей и capabilities.
  https://kotlinlang.org/docs/serialization.html
- Kotlin Time Clock/Instant: используем `kotlin.time.Clock`/`Instant` вместо deprecated `kotlinx.datetime.Clock`.
  https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-clock/
  https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.time/-instant/
- Kotlin Multiplatform: структура shared‑модулей и таргеты.
  https://kotlinlang.org/docs/multiplatform.html
- Ktor client responses: чтение стриминга через `ByteReadChannel`, а не `bodyAsText()`.
  https://ktor.io/docs/client-responses.html
- Ktor ByteReadChannel line APIs: заменили deprecated `readUTF8Line` на `readLine` по исходникам Ktor.
  https://raw.githubusercontent.com/ktorio/ktor/main/ktor-io/common/src/io/ktor/utils/io/ByteReadChannelOperations.kt
- llama.cpp (оф. README) и GGUF tooling: выбран runtime и формат для on-device MVP.
  https://raw.githubusercontent.com/ggml-org/llama.cpp/master/README.md
  https://raw.githubusercontent.com/ggml-org/llama.cpp/master/gguf-py/README.md
- Android NDK + CMake: интеграция native runtime через отдельный Android library module.
  https://developer.android.com/ndk/guides/cmake
- Android Network Security Config: cleartext только в debug для localhost/10.0.2.2, release — HTTPS only.
  https://developer.android.com/training/articles/security-config
- Android NetworkCapabilities/ConnectivityManager: определение наличия проверенного интернета для offline-first UI.
  https://developer.android.com/reference/android/net/NetworkCapabilities
  https://developer.android.com/reference/android/net/ConnectivityManager
- Ollama API: используем `/api/tags` и `/api/chat` со стримингом.
  https://raw.githubusercontent.com/ollama/ollama/main/docs/api.md
- AndroidX Compose BOM: фиксируем версии Compose через BOM и используем AndroidX‑артефакты.
  https://developer.android.com/jetpack/compose/bom
- Ktor Server Auth (Bearer) + Content Negotiation + CallId: используем bearer‑аутентификацию, JSON сериализацию и request id.
  https://ktor.io/docs/server-auth.html
  https://ktor.io/docs/server-content-negotiation.html
  https://ktor.io/docs/server-call-id.html
- AWS SDK v2 S3 Presign: выдаем presigned URL, не раскрывая постоянные креды.
  https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3-presign.html
- SQLDelight (KMP SQLite): локальные таблицы событий/медиа через SQLDelight runtime и платформенные драйверы.
  https://cashapp.github.io/sqldelight/
- Android Keystore: хранение секретов в Keystore‑обертке с AES/GCM.
  https://developer.android.com/privacy-and-security/keystore
- Apple Keychain Services: хранение токенов в Keychain.
  https://developer.apple.com/documentation/security/keychain_services
