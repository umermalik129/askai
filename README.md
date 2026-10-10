# AskAI

A small Android helper that gets selected text or part of your screen to an
AI in a couple of taps. Two ways to send, your choice:

- **Open an app**: hand the text or screenshot to the Claude, ChatGPT,
  Gemini, Perplexity, Grok or Copilot app. No keys, no accounts.
- **Use an API**: ask the Claude API or any OpenAI-compatible endpoint with
  your own key. The answer streams into a popup you can copy from.

## What it does

### Prompts
Save as many prompts as you like, each with a short name such as *Summarize*
or *Translate*, and drag the handle to put the ones you use most at the top.
When you send something, a picker shows the names in that order; with a
single prompt there is nothing to pick and it is used straight away.

### Selectable text
Select text in any app and pick **AskAI** from the selection menu (tap the
`⋮` overflow if it is hidden). Choose a prompt. In open-app mode the text
opens in your AI app with the prompt appended; in API mode the answer
appears in a popup.

### Text you can't select
Some apps (LinkedIn, images, PDFs) don't let you select text. For those:

1. Pull down Quick Settings and tap the **AskAI capture** tile.
2. Allow screen capture.
3. A screenshot appears with a box on it. Drag the box to move it, drag an
   edge or corner to resize it.
4. Tap **Send** and choose a prompt.

The status bar, navigation bar and display cutout are cropped out, so the
image only contains app content.

**Start it with a gesture.** Set AskAI as the digital assistant app (AskAI
settings → *Choose assistant app*, or Android Settings → Apps → Default apps
→ Digital assistant app). The assistant gesture then starts a capture: swipe
up from a bottom corner with gesture navigation, or long-press Home with
button navigation. If the assistant is allowed to use screenshots (a switch
on that same settings page) the capture skips the consent dialog entirely;
otherwise the usual consent flow runs. This replaces Gemini / Google
Assistant on that gesture until you switch back.

### API mode
Pick **Use an API** in settings, choose **Claude** or **OpenAI-compatible**,
and enter a key. The model and, for OpenAI-compatible providers, the base URL
can be changed, so Gemini, OpenRouter, Groq or a local server all work. The
answer streams into a bottom popup. **Copy** puts it on the clipboard and
closes the popup. Screenshots are sent as images, so the model must support
vision. Keys are stored with `EncryptedSharedPreferences`.

### Open-app mode and screenshots
AI apps ignore text that is shared together with an image, so in open-app
mode the app has two ways to deliver the prompt with a screenshot:

- **Write prompt into screenshots** (default): the prompt is drawn as a
  caption under the image and the AI reads it from there.
- Off: the prompt is copied to the clipboard; long-press the chat box in the
  AI app and tap **Paste**.

## Setup

1. Build and install AskAI (see below).
2. Open AskAI and pick how to send: install an AI app, or enter an API key.
3. Tap **Add tile to Quick Settings**. On Android 12 and older, open Quick
   Settings, tap the edit (pencil) button and drag the tile in.
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
| `ProcessTextActivity` | Handles `ACTION_PROCESS_TEXT` from the selection menu, shows the prompt picker, forwards the text. |
| `PromptPicker` | The prompt chooser shared by both paths; skipped when there is at most one prompt. |
| `AnswerActivity` / `LlmClient` | API mode: streams from the Claude Messages API or an OpenAI-compatible `/chat/completions` endpoint over raw HTTP and shows the result. |
| `CaptureTileService` | The Quick Settings tile; launches `CaptureActivity`. |
| `AssistService` / `AssistSession` | Registers AskAI as a digital assistant. On the gesture, uses the system-provided screenshot when available, else launches `CaptureActivity`. |
| `CaptureActivity` | Invisible; asks for `MediaProjection` consent and measures the system bars. |
| `CaptureService` | Foreground service that grabs one frame through a `VirtualDisplay`, strips the bars and saves a PNG. |
| `CropView` / `CropActivity` | The movable, resizable box over the screenshot. |
| `Caption` | Draws the message under the cropped image. |
| `AiApp` | The supported AI apps; sends text or an image to the chosen one, or opens the Play Store if it is missing. |
| `Settings` | Send mode, chosen AI app, API provider and keys, prompts, burn-in toggle. Encrypted. |

Android asks for screen-capture consent on every capture. That is a platform
rule and the app can't skip it. On Android 14+ the dialog is limited to the
entire screen so the single-app picker never appears.

## Notes

- Adding another AI app means one line in the `AiApp` enum and one
  `<package>` entry in the manifest `<queries>` block.
- Screenshots are written to the app's private cache and overwritten on the
  next capture.

## License

MIT
