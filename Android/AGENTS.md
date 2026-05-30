# Hysera Android rules

These rules are mandatory for every change under `Android/`.

- The application name is `Hysera`.
- The canonical logo source is `Brand/hysera-logo.png`.
- Never replace `Brand/hysera-logo.png` without explicit user permission.
- Local Android builds are forbidden.
- Never run `./gradlew assembleDebug` locally.
- Never run `./gradlew build` locally.
- Never run `./gradlew installDebug` locally.
- Never run Gradle tasks locally that create APK or AAB files.
- Never create APK or AAB files on the user's PC.
- Build verification must run only through GitHub Actions.
- Keep Android source files only under `Android/`.
- Keep final APK files only under `Release/`.
- The final debug APK path is `Release/Hysera-debug.apk`.
- Do not remove `Brand/`, `Android/`, or `Release/` without explicit user permission.
- Do not download suspicious or untrusted binaries.
- Pin every sing-box and Xray core version.
- Add native binaries only through a safe and reproducible GitHub Actions process or from pre-verified release assets.
