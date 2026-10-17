# Installing Q&E upgrades without uninstalling

Android requires both an identical application ID (`com.moraswood.qande`) and the **same signing certificate** for every update. An APK signed by GitHub's temporary debug key generally cannot update an APK signed by another runner.

## One-time signing setup

1. Generate one private upload/signing keystore and retain it securely. For example, with Java's `keytool`:
   ```bash
   keytool -genkeypair -v -keystore qe-release.jks -alias qe -keyalg RSA -keysize 3072 -validity 10000
   ```
   Remember the keystore and key passwords. Do not commit this file or the passwords.
2. Create four GitHub **Actions repository secrets** in Settings → Secrets and variables → Actions:
   - `QE_SIGNING_KEYSTORE_B64`: single-line base64 of the binary keystore (e.g. `base64 -w0 qe-release.jks` on Linux, or `base64 < qe-release.jks | tr -d '\\n'` on macOS).
   - `QE_SIGNING_STORE_PASSWORD`: keystore password.
   - `QE_SIGNING_KEY_ALIAS`: `qe` (if using the command above).
   - `QE_SIGNING_KEY_PASSWORD`: key password.
3. Merge the build pull request. Builds on `main` create a **QE-signed-release-apk** artifact; PR builds remain disposable, debug-signed test builds. Only install release builds for long-term use.
4. **One-time migration:** Uninstall the old debug APK before installing the first release-signed APK, because its signature differs. This may delete device-stored quotations and projects; preserve them first. Later release APKs signed with the same keystore update in place and preserve app data. **Do not discard your keystore or change signing passwords/alias.**

Build versionCode increases automatically through GitHub Actions run numbers. Replacing the keystore still breaks upgrades, even with a higher versionCode.

## Important

No backup/export feature has been added yet. Do not uninstall an existing Q&E installation containing important locally saved projects until its data has been safely exported by a verified process. A successful compile does not guarantee that all device features have been tested.
