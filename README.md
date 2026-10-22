# VibeCode

Lightweight Android code editor ("vibe coding") with an AI agent. Runs on low-end devices (2GB RAM).

## Features
- File explorer (drawer) + tabbed Editor / AI Agent
- Code editor with line numbers and syntax highlighting (Kotlin, Java, Python, JS/TS, C/C++, C#, Go, Rust, Bash, SQL, Groovy, markup)
- AI agent supporting OpenAI, Anthropic Claude, Google Gemini (bring your own API key)
- File context sent to the model automatically
- Save files to device storage
- Lightweight: minSdk 24, R8 shrink in release, minimal dependencies, no heavy frameworks

## Build
Open the project in Android Studio (or build with Gradle):
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Usage
1. Install APK.
2. Open Settings (toolbar menu) -> choose provider, paste API key, set model.
3. Open a folder (drawer) -> tap a file to edit.
4. Switch to "AI Agent" tab to chat; current file is included as context.

## Notes
- Storage access: on Android 11+ the app requests "All files access" to read/write your code folders.
- For very large files, highlighting runs on the UI thread; keep files reasonable for smoothness on 2GB devices.
- No code execution built in (keeps it light); use a terminal app if needed.
