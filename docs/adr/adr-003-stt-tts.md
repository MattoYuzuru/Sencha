# ADR-003: STT/TTS Pipelines, Artifacts, and Optional Model Catalog

## Status
Accepted

## Context
Sencha iteration 3 adds speech-to-text (STT) and text-to-speech (TTS) pipelines that must run as jobs, produce syncable artifacts, and remain offline-first. The app currently runs chat inference locally and syncs events/blobs via an append-only log. We also need a path to discover models via an online catalog later in this iteration.

## Decision
1. **Provider strategy**: STT/TTS run through a remote-node provider over HTTPS. This keeps iteration scope manageable and avoids heavy on-device ML integration while keeping the pipeline Job-based and reusable.
2. **Supported audio formats (MVP)**: `audio/m4a`, `audio/wav`, `audio/mpeg`, `audio/mp4`. These are widely supported by Android/iOS pickers and most node runtimes, balancing compatibility and file size.
3. **Capability schema**: explicit `SttCapabilities` and `TtsCapabilities` with validation of formats, sizes, durations, voices, and limits. UI consumes these capabilities to avoid invalid combinations.
4. **Artifacts and sync**: artifacts are stored in SQLDelight (`artifacts` table) and emitted to the event log via `artifact.created`. Blob metadata is included with artifact events, and a `blob.uploaded` event updates remote keys after upload. Blob scope reuses the existing `chat_id` field as a generic scope id (`artifact-<id>`) to avoid server changes.
5. **Job metadata**: STT/TTS jobs persist a lightweight job record (type, model id, payload json, state, error info) to keep job state consistent after restarts.
6. **Optional catalog**: a remote JSON manifest over HTTPS lists models with size/sha256/licensing. The UI filters by runtime and device constraints; installs run as jobs and verify hashes before atomic move.

## Consequences
- Remote node configuration is required for STT/TTS; offline UI disables execution and shows explicit status.
- Audio blobs and artifacts increase local storage footprint and sync traffic.
- The event log remains append-only; artifacts can be replayed without conflict resolution.

## Docs Consulted
- Android ActivityResultContracts.OpenDocument (file picker) and OpenableColumns (metadata)
  https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument
  https://developer.android.com/reference/android/provider/OpenableColumns
- Android MediaPlayer (audio playback)
  https://developer.android.com/reference/android/media/MediaPlayer
- Apple UIDocumentPickerViewController (file picker)
  https://developer.apple.com/documentation/uikit/uidocumentpickerviewcontroller
- Apple AVAudioPlayer and AVAudioSession (audio playback)
  https://developer.apple.com/documentation/avfaudio/avaudioplayer
  https://developer.apple.com/documentation/avfaudio/avaudiosession
- Ktor client multipart requests (audio upload to node)
  https://ktor.io/docs/client-multipart.html
