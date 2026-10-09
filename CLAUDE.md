# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

AskAI is a small Android app (Kotlin, XML views, no Compose) that hands selected text or a cropped
screenshot to a third-party AI app (Claude by default) via `ACTION_SEND`. There is no backend, no
API key, and no network code. Package: `com.technatix.askai`. minSdk 26, target/compile SDK 34.

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
- Capture path cannot be driven from adb: `CaptureActivity` is not exported and the MediaProjection
  consent dialog needs a real tap. `adb shell cmd statusbar add-tile com.technatix.askai/.CaptureTileService` registers the tile.
- Logs: `adb logcat -s AskAI.Capture AndroidRuntime:E`.
- Do not fire share intents at the user's AI apps for testing without telling them first; it opens
  those apps on their phone.

## Architecture

Two independent entry points converge on `AiApp` (enum of supported apps with their package names).
`Settings` (plain SharedPreferences) holds the chosen app, the message list, and the burn-in flag.

**Text path**: `ProcessTextActivity` (invisible theme, `ACTION_PROCESS_TEXT`, label "AskAI" is what
the selection menu shows) → appends the saved message → `AiApp.sendText`. It is a plain `Activity`
so it can use the platform translucent theme; `MainActivity` toggles it on/off via
`PackageManager.setComponentEnabledSetting`.

**Capture path** (four steps, each a separate component because of platform rules):
1. `CaptureTileService` (Quick Settings tile) launches `CaptureActivity` with a `PendingIntent` on API 34+.
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

**Message delivery rules** (shared by both paths): zero saved messages → send alone; one → use it;
several → show a picker. AI apps ignore `EXTRA_TEXT` when an image is attached, which is why
`Settings.burnIn` (default on) draws the message into the screenshot; when off, the message is
copied to the clipboard instead.

**Adding an AI app**: one entry in the `AiApp` enum and a matching `<package>` in the manifest
`<queries>` block, or `isInstalled` will always report false on Android 11+.

## Repo notes

- `_unused_api/` is an earlier version that called the Anthropic/OpenAI APIs directly. It is outside
  the source set and not compiled. Leave it unless asked to delete it.
- The settings screen (`MainActivity`) applies `DynamicColors` and `enableEdgeToEdge()`; keep new
  screens consistent with that and with Material 3 widgets.
- `.gitattributes` normalises to LF. Git identity is set per-repo; commits are pushed to
  `origin/main` only when the user asks.
