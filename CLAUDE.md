# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AskAI is a small Android app (Kotlin, XML views, no Compose) that sends selected text or a cropped
screenshot either to a third-party AI app via `ACTION_SEND` (open-app mode) or straight to the
Claude Messages API / an OpenAI-compatible endpoint over raw OkHttp (API mode) and shows the
streamed answer in a popup. Package: `com.technatix.askai`. minSdk 26, target/compile SDK 34.

## Building and running

There are no `gradlew` wrapper scripts checked in, only `gradle/wrapper/gradle-wrapper.properties`
(Gradle 8.7). Use Android Studio, or a cached Gradle distribution directly:

```
# Gradle 8.7 does not run on JDK 25 (Android Studio's bundled JBR). Use a JDK 17–21.
export JAVA_HOME="C:/Users/admin/.jdks/jbr-21.0.11"
G=$(ls -d ~/.gradle/wrapper/dists/gradle-8.7-bin/*/gradle-8.7/bin/gradle.bat | head -1)

"$G" :app:compileDebugKotlin -q     # fast compile check
"$G" :app:assembleDebug -q          # APK at app/build/outputs/apk/debug/app-debug.apk
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

Python is not installed on this machine; use sed or the Write/Edit tools for file edits.
There are no unit or instrumented tests.

### Manual testing over adb

- Text path: `adb shell am start -a android.intent.action.PROCESS_TEXT -t text/plain --es android.intent.extra.PROCESS_TEXT Word -n com.technatix.askai/.ProcessTextActivity`
  (the extra must be a single word; the shell splits on spaces). The chosen AI app should come to the front.
- Capture path: `adb shell am start -a android.intent.action.ASSIST -n com.technatix.askai/.CaptureActivity`
  opens the MediaProjection consent dialog, which needs a real tap; the rest can't be driven from adb.
  `adb shell cmd statusbar add-tile com.technatix.askai/.CaptureTileService` registers the tile.
- Assistant role: `adb shell cmd role add-role-holder android.app.role.ASSISTANT com.technatix.askai`
  (remove first with `remove-role-holder` if it was already held, or the voice service isn't picked up).
  `adb shell settings get secure voice_interaction_service` should then show `.AssistService`.
  `adb shell input keyevent KEYCODE_ASSIST` fires the gesture when the phone is awake and unlocked.
- Logs: `adb logcat -s AskAI.Capture AndroidRuntime:E`.
- Do not fire share intents at the user's AI apps for testing without telling them first; it opens
  those apps on their phone.

## Architecture

Two entry points (text selection, screen capture) each end in the same fork: `pickPrompt` (in
`PromptPicker.kt`, skipped when ≤1 prompt) then `Settings.mode` decides between `AiApp.sendText` /
`sendImage` (open-app mode) and `AnswerActivity` (API mode). `Settings` is an
`EncryptedSharedPreferences` store holding mode, chosen `AiApp`, `ApiProvider` + keys/models/base
URL, the `Prompt(label, text)` list (JSON), and the burn-in flag.

**Text path**: `ProcessTextActivity` (`Theme.AskAI.Invisible`, `ACTION_PROCESS_TEXT`, label "AskAI"
is what the selection menu shows). It is an `AppCompatActivity` so the Material prompt dialog can
be shown over the host app; `MainActivity` toggles it on/off via
`PackageManager.setComponentEnabledSetting`.

**API path**: `AnswerActivity` (`Theme.AskAI.Popup`, bottom 75 % of the screen) runs `LlmClient.stream`
on `Dispatchers.IO` and appends deltas to a selectable TextView. Copy closes the popup. `LlmClient`
is raw HTTP: Claude uses `/v1/messages` with SSE `content_block_delta` events, images as base64
JPEG content blocks (longest side ≤1568 px), and `fallbacks: "default"` + the
`server-side-fallback-2026-07-01` beta header on models that support it; OpenAI-compatible uses
`/chat/completions` with `image_url` data URIs. Both providers are intentionally raw HTTP rather
than the official SDKs so one small client covers both.

**Capture path** (four steps, each a separate component because of platform rules):
1. `CaptureTileService` (Quick Settings tile) launches `CaptureActivity` with a `PendingIntent` on API 34+.
   The assistant gesture is the other trigger: `AssistService` + `AssistSessionService` +
   `AssistRecognitionService` (all in `AssistService.kt`, declared via `xml/voice_interaction_service.xml`)
   make AskAI a real `VoiceInteractionService`, which is what OEM "digital assistant" pickers list
   (a bare `ACTION_ASSIST` activity was not shown on the user's vivo phone). `AssistSession.onShow`
   waits 600 ms for `onHandleScreenshot`; if the system supplies the bitmap it goes straight to
   `CropActivity` with no consent dialog, otherwise it launches `CaptureActivity`. Activities are
   started with `startAssistantActivity`, the session API that is exempt from background-start rules.
   `CaptureActivity` keeps its `ACTION_ASSIST` filter as a secondary entry point.
2. `CaptureActivity` (invisible) requests MediaProjection consent, restricted to the entire screen on
   API 34+, measures system-bar insets from its own window, then starts `CaptureService` after a
   400 ms delay so the consent dialog has faded. It stays alive to receive the result because a
   service cannot start activities from the background.
3. `CaptureService` is a foreground service of type `mediaProjection` (required on API 34+ before
   `getMediaProjection`). It grabs one `ImageReader` frame from a `VirtualDisplay`, strips the bars,
   writes `cache/captures/screen.png`, and reports back through the static `CaptureService.listener`.
4. `CropActivity` shows the frame in `CropView` (movable/resizable box), optionally draws the message
   under the crop via `Caption`, then `AiApp.sendImage` with a `FileProvider` URI.

Both capture activities use `taskAffinity="com.technatix.askai.capture"` so launching from the tile
never brings the settings screen up underneath the translucent capture window.

**Prompt rules** (shared by both paths): zero prompts → send alone; one → use it; several → picker
showing labels. In open-app mode AI apps ignore `EXTRA_TEXT` when an image is attached, which is
why `Settings.burnIn` (default on) draws the prompt into the screenshot via `Caption`; when off,
the prompt is copied to the clipboard instead. API mode needs neither.

**Adding an AI app**: one entry in the `AiApp` enum and a matching `<package>` in the manifest
`<queries>` block, or `isInstalled` will always report false on Android 11+.

## Repo notes

- The settings screen (`MainActivity`) applies `DynamicColors` and `enableEdgeToEdge()`; keep new
  screens consistent with that and with Material 3 widgets.
- `.gitattributes` normalises to LF. Git identity is set per-repo; commits are pushed to
  `origin/main` only when the user asks.
