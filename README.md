# AskAI

A small Android helper that gets text or part of your screen into the
[Claude](https://play.google.com/store/apps/details?id=com.anthropic.claude)
app in a couple of taps. No API keys, no accounts, nothing leaves the phone
except what you hand to Claude.

## What it does

### Selectable text
Select text in any app and pick **AskAI** from the selection menu (tap the
`⋮` overflow if it is hidden). The text opens in the Claude app.

### Text you can't select
Some apps (LinkedIn, images, PDFs) don't let you select text. For those:

1. Pull down Quick Settings and tap the **AskAI capture** tile.
2. Allow screen capture.
3. A screenshot appears with a box on it. Drag the box to move it, drag an
   edge or corner to resize it.
4. Tap **Send to Claude**.

The status bar, navigation bar and display cutout are cropped out, so the
image only contains app content.

### Messages with a screenshot
In the AskAI app you can save one or more messages, one per line:

| Saved messages | What happens on send |
|---|---|
| none | the screenshot is sent alone |
| one | it is used automatically |
| several | a picker appears and you tap one |

Claude ignores text that is shared together with an image, so the app has two
ways to deliver the message:

- **Write the message into the screenshot** (default): the message is drawn as
  a caption under the image and Claude reads it from there.
- Off: the message is copied to the clipboard; long-press the chat box in
  Claude and tap **Paste**.

## Setup

1. Install the Claude app from the Play Store.
2. Build and install AskAI (see below).
3. Open AskAI and tap **Add Quick Settings tile**. On Android 12 and older,
   open Quick Settings, tap the edit (pencil) button and drag the tile in.
4. Grant the notification permission when asked. It is only used for the
   short-lived "Capturing screen" notification Android requires during
   capture.

## Building

Requirements: Android Studio (or a JDK 17–21 and the Android SDK), minimum
Android 8.0 (API 26), target API 34.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle 8.7 does not run on JDK 25, so if Android Studio's bundled JDK is that
new, point `JAVA_HOME` at a JDK 17 or 21.

## How it works

| Piece | Role |
|---|---|
| `ProcessTextActivity` | Handles `ACTION_PROCESS_TEXT` from the selection menu and forwards the text. |
| `CaptureTileService` | The Quick Settings tile; launches `CaptureActivity`. |
| `CaptureActivity` | Invisible; asks for `MediaProjection` consent and measures the system bars. |
| `CaptureService` | Foreground service that grabs one frame through a `VirtualDisplay`, strips the bars and saves a PNG. |
| `CropView` / `CropActivity` | The movable, resizable box over the screenshot. |
| `Caption` | Draws the message under the cropped image. |
| `ClaudeApp` | Sends text or an image to `com.anthropic.claude`, or opens the Play Store if it is missing. |
| `Settings` | Saved messages and the burn-in toggle. |

Android asks for screen-capture consent on every capture. That is a platform
rule and the app can't skip it. On Android 14+ the dialog is limited to the
entire screen so the single-app picker never appears.

## Notes

- `_unused_api/` holds an earlier version that called the Anthropic and OpenAI
  APIs directly. It is not compiled and can be deleted.
- Screenshots are written to the app's private cache and overwritten on the
  next capture.

## License

MIT
