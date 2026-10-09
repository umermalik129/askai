# AskAI

A small Android helper that gets text or part of your screen into your AI
app in a couple of taps. No API keys, no accounts, nothing leaves the phone
except what you hand to the AI app.

## What it does

### Pick your AI app
In AskAI settings choose where everything goes: **Claude** (default),
**ChatGPT**, **Gemini**, **Perplexity**, **Grok** or **Copilot**. Apps that
aren't installed are marked, with a Play Store shortcut.

### Selectable text
Select text in any app and pick **AskAI** from the selection menu (tap the
`⋮` overflow if it is hidden). The text opens in your AI app with your
saved message appended, so it knows what to do with it.

### Text you can't select
Some apps (LinkedIn, images, PDFs) don't let you select text. For those:

1. Pull down Quick Settings and tap the **AskAI capture** tile.
2. Allow screen capture.
3. A screenshot appears with a box on it. Drag the box to move it, drag an
   edge or corner to resize it.
4. Tap **Send**.

The status bar, navigation bar and display cutout are cropped out, so the
image only contains app content.

### Messages
In the AskAI app you can save one or more messages, one per line. They apply
to both selected text and screenshots:

| Saved messages | What happens on send |
|---|---|
| none | the text or screenshot is sent alone |
| one | it is used automatically |
| several | a picker appears and you tap one |

For selected text the message is simply appended after the text. For
screenshots, AI apps ignore text that is shared together with an image, so
the app has two ways to deliver the message:

- **Write the message into the screenshot** (default): the message is drawn as
  a caption under the image and the AI reads it from there.
- Off: the message is copied to the clipboard; long-press the chat box in
  the AI app and tap **Paste**.

## Setup

1. Install the AI app you want to use from the Play Store.
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
| `AiApp` | The supported AI apps; sends text or an image to the chosen one, or opens the Play Store if it is missing. |
| `Settings` | Chosen AI app, saved messages and the burn-in toggle. |

Android asks for screen-capture consent on every capture. That is a platform
rule and the app can't skip it. On Android 14+ the dialog is limited to the
entire screen so the single-app picker never appears.

## Notes

- Adding another AI app means one line in the `AiApp` enum and one
  `<package>` entry in the manifest `<queries>` block.
- `_unused_api/` holds an earlier version that called the Anthropic and OpenAI
  APIs directly. It is not compiled and can be deleted.
- Screenshots are written to the app's private cache and overwritten on the
  next capture.

## License

MIT
