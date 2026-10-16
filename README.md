# Moras Wood Q&E V10 Android

This project embeds the V10 website assets in an Android WebViewAssetLoader. The home screen provides New Project, Saved Projects, and Notes. The workspace includes Calculator, Estimation, Quotation, Job Review, and Settings. Project autosave and reusable notes are stored locally on the device. The Settings tab includes a general AI API configuration and a length converter for mm, cm, m, inches, feet, yards, and miles.

### Building through GitHub
Place both `q-and-e-android/` and `.github/workflows/q-and-e-v10-android.yml` at the ROOT of the `Jfmoras/Apps` repository (replacing earlier `q-and-e-android` files). Go to GitHub Actions > Build Q&E V10 Android APK > Run workflow, then download `QE-V10-debug-apk` artifact. You can also open `q-and-e-android` in Android Studio and build with JDK 17 / Gradle 8.9.

### Limitations
- Local project storage on device does not automatically import projects saved in a browser or on Netlify V8. Clearing Android app data removes locally saved work.
- AI description improvement requires an API key and an internet connection. The app recognizes common OpenAI, Anthropic, Google Gemini, Groq, OpenRouter, xAI, and Perplexity key prefixes and suggests an endpoint and default model. Provider/model access depends on the user's account; defaults may need updating in Settings. Unknown key formats can still be used by entering a compatible provider's endpoint and model. Requests go directly to the configured provider, not through a Moras Wood server. Android encrypts the key with Android Keystore; browser mode stores it in that browser's local storage.
- Original V10 browser printing is overridden to invoke Android system print, which supports Save as PDF. PDF and layout need testing on a physical device.
- Build with Android Studio / JDK 17, or use the GitHub Actions workflow described above. This is a debug/test build, not a Play Store release.
