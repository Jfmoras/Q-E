# Q&E V10.1 → native Compose + Room port plan

## Target decision
- **Repo/branch: `Jfmoras/Q-E` @ `feature/mwi-native-compose-rebuild`, Gradle project in `native-android/`.**
  - It already contained a Compose + Room (kapt) project with 30 green "Build MWI Native Alpha" runs, the same
    applicationId as V10.1 (`com.moraswood.qande`) and it lives next to the V10.1 sources and the four QE signing secrets.
  - `Jfmoras/MWI-DASHBOARD.-` uses applicationId `com.moraswood.mwidashboard` and different signing secrets
    (`Q_E_KEY_*`, base64 empty in run #16) — an APK from there could never update the installed V10.1 app. Not touched.
- Previous alpha code on the branch was a redesign (different labels/flows, wrong `TimberMath.volume` heuristic
  width≤6 → CFT) — replaced, not extended.

## Architecture
- `MainActivity` (ComponentActivity, same component name as V10.1) → `QeApp` (Navigation Compose).
- Routes = V10.1 views: home, workspace (tabs calculator/estimation/quotation/review), notes, quick, hoppus, settings, operations (tabs projects/inventory/machines/reports). Every route is pushed directly above home (V10.1 history behaviour); back on home → `finish()`.
- `AppViewModel` holds `WorkspaceState` (exact app.js `state` mirror, kotlinx.serialization with loose string/number decoding) and autosaves it as JSON in Room `kv_store["mwi-autosave-v1"]` on every edit.
- Room (`mwi-qe.db`, v1): kv_store (autosave, quick calc, hoppus), saved_projects (snapshot JSON), saved_notes, ops_projects, ops_project_entries, ops_attachments (files in filesDir/project-attachments), ops_materials, ops_movements, ops_machines, ops_machine_history, ops_audit.
- Backup: export/import the **V10.1 `moras-qe-backup` v1 JSON** (storage map of `mwi-*` keys) so existing V10.1 backups restore; Room ↔ storage-key mapping in a `BackupCodec`.
- Pure math in `QeMath.kt` (toFeet/toInches/calcRow/surfaceSqft/labourAreaSqft/itemTotals/costingResult/quoteTotals/hoppus/converter) + JVM unit tests asserting V10.1 numbers.
- Formatting helpers: `money()` (en-IN INR 0 dp), `rs()`, `fmt()` (ops, 2 dp), `fmtMeasure()`.
- Print: Android `PrintManager` with a custom `PrintDocumentAdapter` drawing the V10.1 A4 print sheet on `PdfDocument` canvas (no WebView), job name "Moras Wood Q&E Quotation".
- AI: `HttpURLConnection` client copied from V10.1 MainActivity (same prompts/endpoints/errors), key in SharedPreferences `ai_settings` with AndroidKeyStore alias `com.moraswood.qande.openai` AES/GCM (same prefs file/keys → a key saved by V10.1 keeps working after update).
- Images: SAF picker → resize max 720 px JPEG q65 → data URL strings in `QuoteItem.images` (same as V10.1).
- Attachments: SAF picker → private file copy (≤15 MB), preview: images via BitmapFactory, PDF via `PdfRenderer` (no WebView), else message; "Download / Open externally" via FileProvider ACTION_VIEW.

## Milestones (each = one or more commits, green CI before moving on)
| # | Milestone | V10.1 features |
|---|---|---|
| 1 | **Skeleton** ✅ | Gradle (Compose, Navigation, Room+kapt, serialization), permanent signing + signed-release workflow, V10.1 charcoal theme, top bar (logo, title, Home), Home grid (8 actions, glyphs), Saved Projects panel shell, workspace tabs + project toolbar, Operations header/nav, placeholder screens, Room schema for every V10.1 data shape, launcher icons, configChanges, back fix |
| 2 | **Calculator** ✅ (+ template library, item photo capture) | workspace Timber Calculator (items, rows, unit selects, converted hint, Enter-key flow, item & grand totals, Send to Estimation); General CFT/KOL calculator with colour groups (`mwi-quick-calc-v1`); Hoppus log volume (`mwi-hoppus-v1`); QeMath + unit tests |
| 3 | **Estimation** ✅ (+ Feature B handoff, estimation photos, estimation template logging) | estimation cards, measurement table, rate groups (group/ungroup/remove, colours), wood/labour/polish subcards, consumables/other, auto selling price, metrics, Send this item / Send to Quotation with validation, new estimation item |
| 4 | **Quotation** | letterhead, date, customer, options (add/duplicate/remove/select/rename), items (desc, units datalist, qty, price, total, remove), images (≤6, resize), AI improve/undo, discount/GST totals, notes (saved notes select/add/delete, save/remove), print/Save-as-PDF sheet (all options), Export to Projects |
| 5 | **Projects/payments** | Save Project (revisions, read-only guard), Saved Projects panel (View/Print, Revise, Delete), Continue Current Project, New Project confirm, Job Review tab; Operations › Projects (create with document upload, import quotation, pending import from Export to Projects, search, totals, notes, status, entries payment/revision/expense, history, delete; **+ Feature A: item photos carried into the project on quotation→project conversion**), attachments (upload/list/open preview image/PDF/remove), Expenses & Inventory (materials, movements, stock, low-stock, history) |
| 6 | **Machines** | Operations › Machines & Tools (register MWI-0001…, status, worker, history) and Monthly Reports; **+ Feature C asset tracking (below)** |
| 7 | **Settings** | Full Backup & Restore (V10.1 JSON format, confirm, restore; **+ item photos & native template tables in the backup — Feature A**), AI API settings (detection, defaults, save/remove, encrypted key), Length converter, Saved Notes manager |
| 8 | **Final signed build** | parity review against V10.1-inventory.md, remove placeholders, README, final signed APK artifact `Q-E-native-permanently-signed-apk` |

## Rules for later phases
- Source of truth: `/workspace/qe-port/src-v101/` (copies of app.js, styles.css, index.html, operations.*) and `V10.1-inventory.md`.
- Build locally first: `source /workspace/qe-port/env.sh; gradle -p native-android --no-daemon --stacktrace :app:testReleaseUnitTest :app:assembleRelease` (toolchain in /workspace/qe-port/tools, local.properties is git-ignored). Worktree: `/workspace/qe-port/work`.
- Push with the GitHub connector `push_files` (one commit per batch). File deletions use `delete_file` with `[skip ci]` in the message and are pushed BEFORE the batch commit so CI only builds the final tree.
- Binary files can't be pushed through the connector: the logo is copied at build time from `app/src/main/assets/mwi-logo.png` (do not delete the legacy file).
- V10.1 quirk to decide in milestone 4/5: operations import note joins lines with a literal `\n` (JS `'\\n'`).

## Native additions requested by Jason (not in V10.1; added without changing V10.1 logic or screens)

### Template library (t4u) — landed in milestone 2
- Room v2 (real `MIGRATION_1_2`, no destructive fallback; `MigrationSchemaTest` checks the SQL against `schemas/2.json`): `job_component_log`, `templates`, `template_rows`.
- Silent logging: 1.5 s after any calculator edit each item with a type (optional "Furniture type tag", else the item name) and rows is re-logged under jobKey = projectId (once saved) or the session key `native-job-session-v1` (new on New Project).
- Matching (`templates/TemplateMatcher.kt`, unit-tested): normalised type (lowercase, punctuation stripped, de-pluralised, token-sorted Levenshtein ≥ 0.8) → per-job components in common units (V10.1 toFeet / toInches) → greedy best-pair component score (L 0.30, W 0.30, T 0.25, qty 0.15 relative similarity, ×0.7 if CFT/KOL differs; unpaired rows score 0 so row-count differences count) → jobs ≥ 0.75 cluster around the job with most neighbours; 2+ jobs → banner with per-component medians in the representative job's units. Saved templates of the same type are offered first.
- Banner above the calculator item list for the first typed item whose rows are still blank: "This looks like your last N <type>s — use that template?" Use template / Dismiss. "Save as template" per item; Settings › Templates lists/deletes.
- Estimation milestone (done): Estimation-tab items are logged too (itemIndex offset 1000) unless they are the unchanged copy sent from the calculator or an unnamed "Item n"; a manually added estimation item without rows gets the same banner on the Estimation tab and "Use template" fills its rows. `tag` is carried by Send to Estimation.
- Projects milestone: re-key session logs to the projectId on Save Project.

### Feature A — one photo per item (t5u)
- Landed in milestone 2: camera (TakePicture → FileProvider `${applicationId}.files`, `item_photos/`, no CAMERA permission) or gallery (PickVisualMedia, no storage permission); copied to `filesDir/item_photos/`, file name in `CalcItem.photo` (JSON, ignored by V10.1) + `item_photos` registry table; downsampled EXIF-rotated thumbnail; Retake / Replace / Remove; carried by Send to Estimation.
- Milestone 3 (done): photo box (thumbnail + Take/Choose/Replace/Remove) on every estimation card. Milestone 5: carry photos into the Operations project on quotation→project conversion. Milestone 7: include `item_photos/` in Full Backup.

### Feature B — calculator → estimation handoff (t5u) — landed in milestone 3
- V10.1 already has it: grand-bar **"Send to Estimation →"** copies items (trimmed name or "Item n", rows with any value) to `estimationItems`, resets costings and opens the Estimation tab, whose cards show each item's CFT/KOL from those rows. Kept exactly (ported in milestone 2); milestone 3 ports the Estimation screen so it opens pre-filled and only rates, labour, polish, consumables, other and selling price are entered.

### Feature C — asset / tool / machine tracking (t6u) — milestone 6
- Separate "Assets" section next to the V10.1 Machines & Tools tab (V10.1 Machines untouched). New Room version with a real migration.
- Asset registry: name, category (machine/tool/accessory), purchase date, cost, serial (optional), photo (optional, app-private like item photos).
- Append-only `asset_issues` (asset, worker, date issued, due-back default today+2 editable, notes) and `asset_returns` (date, condition notes): insert-only DAOs + SQLite triggers `RAISE(ABORT)` on UPDATE/DELETE.
- Corrections: append-only `asset_corrections` referencing the original entry, plus append-only decisions (approved/rejected). Derived state uses only approved corrections.
- Derived status (pure Kotlin, unit-tested): in stock / issued / overdue (open issue past due date).
- Owner gating: owner PIN set on first use, PBKDF2 salted hash; required to open the review queue and approve/reject. Workers can file issues/returns/corrections only.
- Overdue alerts: WorkManager daily PeriodicWorkRequest, notification channel, POST_NOTIFICATIONS request on Android 13+, `work-runtime-ktx`; one notification per overdue issue naming asset and worker.
- UI: asset list with status chips (green in stock, amber issued, red overdue, V10.1 palette), tap → full history timeline.

## Milestone 3 notes / deviations
- Estimation math lives in `logic/QeEstimate.kt` (labourAreaSqft, rateGroupForRow, effectiveTimberRate, groupSummary, costingResult incl. its selling-price side effect, estimateGrand). `QeEstimateTest` compares against output of the unchanged app.js functions run in node (`src/test/resources/estimation/`).
- The renderEstimation() side effects (missing costings created, auto selling price refreshed) are applied on load and after every state change (`QeEstimate.syncCostings`).
- Send to Quotation / Send this item write `quoteItems` and switch to the Quotation tab exactly like V10.1; the Quotation screen itself is milestone 4.
- Missing selling price: status text + input-error styling + focus; V10.1 additionally calls scrollIntoView (Compose brings the focused field into view).
- Layout follows the V10.1 phone breakpoint (≤850 px: single-column subcards, 2×2 metrics).
