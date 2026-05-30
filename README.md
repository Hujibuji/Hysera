# Hysera

Hysera is an Android VPN client scaffold with a Material 3 UI. The application namespace and `applicationId` are both `space.kalloware.hysera`.

The primary VPN adapter is sing-box. Xray is the fallback adapter for configs, protocols, transports, or combinations that sing-box cannot handle correctly, and it can also be selected manually per config.

## Current status

Hysera already includes:

- a Kotlin, Jetpack Compose, Material 3 Android application;
- phone-friendly screens for connection status, saved configs, config entry, subscriptions, logs, and settings;
- light and dark themes;
- local config persistence for raw JSON and supported URI formats;
- structural detection for sing-box JSON, Xray JSON, `vless://`, `vmess://`, `trojan://`, `ss://`, `hysteria2://`, and `hy2://`;
- `CoreEngine`, `SingBoxEngine`, `XrayEngine`, `ConfigDetector`, `ConfigRepository`, and `NativeCoreBridge`;
- Android `VpnService.prepare()` permission handling;
- a foreground `VpnService` with the notification title `Hysera VPN is running`;
- TUN interface creation, state transitions, and in-memory event logs.

The checked-in application intentionally does **not** claim to route real VPN traffic yet. Native sing-box and Xray cores are not bundled. The current `VpnService` creates a safe TUN placeholder without adding device traffic routes, then records which adapter would be used.

## Repository layout

- Android source: `Android/`
- Canonical logo source: `Brand/hysera-logo.png`
- GitHub Actions workflow: `.github/workflows/android-build.yml`
- Generated debug APK: `Release/Hysera-debug.apk`
- GitHub Actions artifact: `Hysera-debug-apk`

`Brand/hysera-logo.png` is the canonical Hysera logo. Do not replace it without permission. If it is absent, the Android project uses a checked-in VectorDrawable fallback. During a GitHub Actions build, an existing PNG is copied to `Android/app/src/main/res/drawable-nodpi/hysera_logo.png` and used by the app UI and adaptive launcher icon.

## GitHub-only build

Android builds are allowed only on GitHub Actions. Do not run Gradle, `assembleDebug`, `build`, `installDebug`, or any APK/AAB-producing command on the user's PC.

To start a build:

1. Push a commit to GitHub, or open the repository's **Actions** tab.
2. Select **Hysera Android Build**.
3. For a manual build, choose **Run workflow** (`workflow_dispatch`).
4. Download the `Hysera-debug-apk` artifact after the job succeeds.

The workflow builds `Android/app`, copies the resulting debug APK to `Release/Hysera-debug.apk`, uploads the artifact, and attempts to commit the generated APK back to `Release/`. Artifact upload still succeeds if repository branch protection prevents the bot commit.

## Subscription support

Hysera can import a subscription URL or raw subscription text from the **Hysera Subscriptions** screen. Subscription data is downloaded directly by the app and stored locally. Hysera does not send configs to third-party parsing services.

Supported metadata header lines:

- `#profile-update-interval`: refresh interval in hours, for example `#profile-update-interval: 1`;
- `#profile-title`: subscription title;
- `#subscription-userinfo`: `upload`, `download`, `total`, and Unix timestamp `expire` values;
- `#support-url`: support link opened through an Android intent;
- `#profile-web-page-url`: profile page link opened through an Android intent;
- `#announce`: plain-text announcement;
- `#announce: base64:...`: Base64-encoded UTF-8 announcement.

Unknown `#` metadata fields and empty lines are ignored. A broken node does not reject the complete subscription: valid nodes are retained and parsing errors are written to **Hysera Logs**.

VPN nodes after the metadata lines may use:

- `vless://`;
- `vmess://`;
- `trojan://`;
- `ss://`;
- `hysteria2://`;
- `hy2://`;
- sing-box JSON;
- Xray JSON.

Use **Проверить подписку** to preview parsing results and **Импортировать** to save a subscription. Saved URL-based subscriptions have an **Update** button for manual refresh. The parsed `#profile-update-interval` value is stored with each profile; periodic WorkManager scheduling remains an explicit TODO and defaults conceptually to 24 hours when the metadata header is absent.

## Adding native cores

Use only pinned, reviewed sing-box and Xray versions. Add native libraries under `Android/app/src/main/jniLibs/` and optional core assets under `Android/app/src/main/assets/cores/`.

Do not build native binaries on the user's PC and do not download untrusted binaries. Add them through a reproducible GitHub Actions process or from pre-verified release assets. Then implement the explicit TODOs in `NativeCoreBridge` so the selected adapter receives the TUN file descriptor and lifecycle events.

## Release signing

The current workflow produces an installable debug APK for manual testing. For a production release:

1. Create a release keystore in a controlled environment.
2. Store the keystore and passwords as GitHub Actions secrets.
3. Add a release signing config in the GitHub build path without committing secrets.
4. Build a signed release APK or AAB on GitHub Actions only.
