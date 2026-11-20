# MWI native Android rebuild (alpha scaffold)

This is a **separate native Android application source** inside the existing repository, on branch `feature/mwi-native-compose-rebuild`. It is NOT production-ready and does NOT replace the current WebView app.

## Implemented
- Kotlin + Jetpack Compose Android UI, no WebView.
- Room entities for projects, project entries, and attachments.
- Android system document picker; copies attachments into private app storage.
- FileProvider ACTION_VIEW for external PDF, image, Office document viewers.
- Import of project metadata from the existing MWI JSON backup.
- Same application ID as current app, reserved for a future signed in-place upgrade.

## Not implemented yet
- All legacy calculators, estimation, quotation editor, printing, rates, notes, inventory and machines.
- Full-fidelity migration of quotations, payment/expense history, images and IndexedDB attachments.
- Native ZIP backup/restore of Room database and attachment files.
- QA tests, release signing verification, CI build and actual device testing.

## Safety
Do not install over the production app yet. The source uses the same application ID, and a signed upgrade must not happen until the full migration is verified. Do not uninstall or clear the old app. The existing JSON backup does not include IndexedDB attachments.

## Build
Open `native-android/` as an Android Studio project. The Gradle wrapper is not bundled in this scaffold; use Android Studio's Gradle installation. Run `assembleDebug` after syncing dependencies. No successful build has been verified yet.
