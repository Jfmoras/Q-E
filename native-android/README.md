# Q&E V10.1 — native Android port (Jetpack Compose + Room)

Branch `feature/mwi-native-compose-rebuild`. A screen-for-screen native port of the Q&E V10.1 hybrid
WebView app (branch `feature/qe-pricing-options-revisions-20261119`, commit `7f901cc`). **No WebView**:
every screen is Compose, all data is in Room. Labels, colours, calculations and flows follow V10.1 exactly.

- applicationId `com.moraswood.qande` (same as V10.1), launcher activity `.MainActivity` (same component name).
- Signed by CI with the permanent `moras-qe` key (secrets `QE_KEYSTORE_BASE64`, `QE_KEYSTORE_PASSWORD`,
  `QE_KEY_PASSWORD`, `QE_KEY_ALIAS`), versionCode = 100 + workflow run number, so each build installs as an
  update over V10.1 (versionCode 11) and over the previous native build.
- Room via kapt; schema exported to `app/schemas/`.
- The letterhead logo is copied at build time from `../app/src/main/assets/mwi-logo.png` (keep that file).

## Milestones
1. Skeleton (theme, header, Home, navigation, workspace tabs, Operations tabs, Room schema, signing) — done
2. Calculator (workspace calculator, General CFT/KOL, Hoppus)
3. Estimation
4. Quotation (options, images, AI, notes, print/PDF, export to projects)
5. Projects/payments (saved projects & revisions, Job Review, Operations projects, inventory, attachments)
6. Machines (machines, monthly reports)
7. Settings (backup/restore in V10.1 JSON format, AI API, length converter, saved notes)
8. Final signed build

**Before installing over V10.1:** export a Full Backup from V10.1 Settings. WebView localStorage is not read by
the native app; restore the backup in the native Settings once that milestone lands. IndexedDB project
attachments are not in that backup — export them separately.

## Build
`gradle -p native-android :app:assembleRelease` (JDK 17, Gradle 8.9, Android SDK 35). Without the signing
env vars the release APK is unsigned.
