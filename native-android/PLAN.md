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
| 4 | **Quotation** ✅ (+ Save Project / Saved Projects revisions, Job Review tab) | letterhead, date, customer, options (add/duplicate/remove/select/rename), items (desc, units datalist, qty, price, total, remove), images (≤6, resize), AI improve/undo, discount/GST totals, notes (saved notes select/add/delete, save/remove), print/Save-as-PDF sheet (all options), Export to Projects |
| 5 | **Projects/payments** ✅ (+ Feature A carry-over, template logs keyed by project id) | (Save Project, Saved Projects panel and Job Review landed in milestone 4) Operations › Projects (create with document upload, import quotation, pending import from Export to Projects, search, totals, notes, status, entries payment/revision/expense, history, delete; **+ Feature A: item photos carried into the project on quotation→project conversion**), attachments (upload/list/open preview image/PDF/remove), Expenses & Inventory (materials, movements, stock, low-stock, history) |
| 6 | **Machines** ✅ | Operations › Machines & Tools (register MWI-0001…, status, worker, history) and Monthly Reports; **+ Feature C asset tracking (below)** |
| 7 | **Settings** ✅ | Full Backup & Restore (V10.1 JSON format, confirm, restore; **+ item photos & native template tables in the backup — Feature A**), AI API settings (detection, defaults, save/remove, encrypted key), Length converter, Saved Notes manager |
| 8 | **Final signed build** | parity review against V10.1-inventory.md, remove placeholders, README, final signed APK artifact `Q-E-native-permanently-signed-apk` |

## Rules for later phases
- Source of truth: `/workspace/qe-port/src-v101/` (copies of app.js, styles.css, index.html, operations.*) and `V10.1-inventory.md`.
- Build locally first: `source /workspace/qe-port/env.sh; gradle -p native-android --no-daemon --stacktrace :app:testReleaseUnitTest :app:assembleRelease` (toolchain in /workspace/qe-port/tools, local.properties is git-ignored). Worktree: `/workspace/qe-port/work`.
- Push with the GitHub connector `push_files` (one commit per batch). File deletions use `delete_file` with `[skip ci]` in the message and are pushed BEFORE the batch commit so CI only builds the final tree.
- Binary files can't be pushed through the connector: the logo is copied at build time from `app/src/main/assets/mwi-logo.png` (do not delete the legacy file).
- V10.1 quirk kept (milestone 5): the operations quotation import joins the notes lines with a literal `\n` (JS `"\\n"`).

## Native additions requested by Jason (not in V10.1; added without changing V10.1 logic or screens)

### Template library (t4u) — landed in milestone 2
- Room v2 (real `MIGRATION_1_2`, no destructive fallback; `MigrationSchemaTest` checks the SQL against `schemas/2.json`): `job_component_log`, `templates`, `template_rows`.
- Silent logging: 1.5 s after any calculator edit each item with a type (optional "Furniture type tag", else the item name) and rows is re-logged under jobKey = projectId (once saved) or the session key `native-job-session-v1` (new on New Project).
- Matching (`templates/TemplateMatcher.kt`, unit-tested): normalised type (lowercase, punctuation stripped, de-pluralised, token-sorted Levenshtein ≥ 0.8) → per-job components in common units (V10.1 toFeet / toInches) → greedy best-pair component score (L 0.30, W 0.30, T 0.25, qty 0.15 relative similarity, ×0.7 if CFT/KOL differs; unpaired rows score 0 so row-count differences count) → jobs ≥ 0.75 cluster around the job with most neighbours; 2+ jobs → banner with per-component medians in the representative job's units. Saved templates of the same type are offered first.
- Banner above the calculator item list for the first typed item whose rows are still blank: "This looks like your last N <type>s — use that template?" Use template / Dismiss. "Save as template" per item; Settings › Templates lists/deletes.
- Estimation milestone (done): Estimation-tab items are logged too (itemIndex offset 1000) unless they are the unchanged copy sent from the calculator or an unnamed "Item n"; a manually added estimation item without rows gets the same banner on the Estimation tab and "Use template" fills its rows. `tag` is carried by Send to Estimation.
- Milestone 5 (done): Save Project re-keys the session logs to the project id (`rekeyLogs`); viewing/revising a saved project starts a fresh session so later unsaved work is not merged into it.

### Feature A — one photo per item (t5u)
- Landed in milestone 2: camera (TakePicture → FileProvider `${applicationId}.files`, `item_photos/`, no CAMERA permission) or gallery (PickVisualMedia, no storage permission); copied to `filesDir/item_photos/`, file name in `CalcItem.photo` (JSON, ignored by V10.1) + `item_photos` registry table; downsampled EXIF-rotated thumbnail; Retake / Replace / Remove; carried by Send to Estimation.
- Milestone 3 (done): photo box (thumbnail + Take/Choose/Replace/Remove) on every estimation card. Milestone 5 (done): Export to Projects carries the estimation (else calculator) item photos; "Create project & attach files" copies them into the project as image attachments ("<item> photo.jpg"). Milestone 7: include `item_photos/` in Full Backup.

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

## Milestone 4 notes / deviations
- Quotation math and option handling in `logic/QeQuote.kt` (totalsForOption, quoteTotals, ensureQuoteOptions, syncActiveOption, activateOption, add/duplicate/remove). `QeQuoteTest` replays scripted option edits against the unchanged app.js lines 5-73 + handlers 417-419 run in node (`test/resources/quotation/v101-harness.js`), comparing every intermediate state, totals, `rs()`/`money()` strings and labels.
- ensureQuoteOptions + syncActiveOption (renderQuotation/renderPrintSheet side effects) run on load and after every state change.
- Print / Save as PDF: `print/QuotePrint.kt` — PrintManager + PrintDocumentAdapter drawing the V10.1 `@media print` sheet on PdfDocument (A4, 190 mm content, same texts, sizes, colours, column widths, image rules, totals block, notes, footer). Page breaks follow the CSS: table rows, totals and notes never split; the table header repeats after a break like a printed `<thead>`. Fonts are Android sans-serif instead of Arial.
- Images: GetMultipleContents("image/*") (the WebView file chooser equivalent) → EXIF-oriented, max 720 px, JPEG q65 data URL, same alerts. The storage-full alert path does not exist (Room has no 5 MB localStorage quota).
- AI improve/undo uses the V10.1 SharedPreferences `ai_settings` + AndroidKeyStore key, so a key saved in V10.1 keeps working; the Settings form is milestone 7.
- Export to Projects writes `mwi-project-import-pending` and opens Operations › Projects; consuming the import is milestone 5.
- Save Project / Saved Projects & Revisions (View / Print, Revise, Delete, read-only guard, status texts) were pulled into this milestone because saved quotations and revisions are part of the quotation flow.
- Units datalist is a text field with a ▾ suggestion menu. Saved-note select and AI style are UI state (DOM-only in V10.1).

## Milestone 5 notes / deviations
- Operations logic in `logic/QeOps.kt` (stock, cost, paid, value, expense, fmt, JS number strings, create/entry/material/movement handlers, quotation imports). `QeOpsTest` runs the unchanged operations.js in node on a fake DOM (`test/resources/operations/v101-harness.js`) and compares every step: stored records, #message, import form values (incl. the literal `\n` notes quirk and toFixed(2)), rendered ₹ figures, history, stock and movement lines.
- Data stays in the existing Room tables (ops_projects / ops_project_entries / ops_attachments / ops_materials / ops_movements / ops_audit; no schema change). Status changes and deletions are audited like V10.1.
- Attachments: SAF multi-select → private copies in filesDir/project-attachments (15 MB limit, same messages); preview: images via BitmapFactory, PDFs via PdfRenderer pages in a scrollable panel (no WebView); "Download / Open externally" hands the file to another app via FileProvider.
- Opening Operations behaves like a fresh operations.html load (Projects tab, empty form/message); only Export to Projects consumes `mwi-project-import-pending`.
- Deviations: an expanded project card stays expanded after Add entry / status change (V10.1 re-rendered and collapsed it); notes save when the field loses focus (the textarea `change` event); "No app on this device can open …" message when nothing can open an attachment; a line "Item photos from the quotation (n) will be attached." appears when Feature A photos are pending.
- Machines & Tools and Monthly Reports: done in milestone 6.

## Milestone 6 notes / deviations
- Machines & Tools and Monthly Reports are exact ports (`QeOps.addMachine/changeMachine/report`); QeOpsTest's V10.1 harness now also replays machine registration, status / worker changes and monthly summaries (current month, empty month, default month).
- Machine edits use an SQL UPDATE (never REPLACE, which would cascade-delete the machine's history); every status / worker change appends `{at,status,worker}` like V10.1. V10.1 does not display the history, neither does the port.
- Monthly Reports month field: V10.1 used `<input type=month>`; native uses a YYYY-MM field with ‹ › month buttons. Same default (current UTC month) and the same `at.startsWith(month)` matching.
- Saved Notes screen ported (create / duplicate check / statuses as V10.1). Per Jason: notes show as text with Edit (Save / Cancel, in place) and Delete with a confirm; Quotation tab gained "Edit saved".
- Feature C (Assets tab, native addition): Room v3 with MIGRATION_2_3 (5 new tables, nothing dropped or altered) + 8 BEFORE UPDATE/DELETE triggers that RAISE(ABORT) on the issue / return / correction / decision logs (also re-applied on every open with IF NOT EXISTS). AssetDao has insert/query methods only.
  - Status is derived, never stored: in stock / issued / overdue (overdue = open issue whose due-back date is before today; the due date itself is not overdue). Default due-back = today + 2 days.
  - Corrections reference an issue or return and change nothing until an approved decision exists; approved corrections overlay fields (later approval wins), can clear the due date or void an entry. Originals stay in the history timeline (voided entries struck through).
  - Owner review queue behind a 4–8 digit PIN: PBKDF2WithHmacSHA256, 16-byte random salt, 120 000 iterations, constant-time compare, 30 s lockout after 5 wrong tries; re-locks when Operations is reopened.
  - Daily WorkManager job (unique periodic, KEEP) posts one notification per overdue asset ("Overdue: <asset>", "Issued to <worker> · due back <date>") on channel `asset-overdue`; POST_NOTIFICATIONS is requested from the Assets tab on Android 13+.
  - Asset photos reuse the item photo store (filesDir/item_photos). Asset data is not in the JSON backup yet (milestone 7).
  - Tests: AssetTrackingTest (status, corrections, PIN incl. a PBKDF2 known-answer vector, v2→v3 migration vs the exported schema, and the triggers on a real SQLite via sqlite-jdbc on the test classpath only).

## Milestone 7 notes / deviations
- Full Backup & Restore (`backup/QeBackup.kt`, pure Kotlin over a small `SqlPort`, so the same code runs on Room and on sqlite-jdbc in tests):
  - File = V10.1 envelope `{format:'moras-qe-backup',version:1,date,storage}` written in the `JSON.stringify(…,null,2)` layout, file name `moras-qe-full-backup.json`.
  - The V10.1 keys (`mwi-autosave-v1`, `mwi-projects-v1`, `mwi-saved-notes-v1`, `mwi-operations-v1`, `mwi-quick-calc-v1`, `mwi-hoppus-v1`, `mwi-ai-settings-v1` without key, other `mwi-` kv rows) are rebuilt from Room in the V10.1 shapes (JS number formatting), so the file still restores in V10.1.
  - Native data rides in two extra `mwi-` string keys that V10.1's validation accepts: `mwi-native-db-v1` (every Room table row-by-row: revisions, notes, operations incl. machine history/audit/attachment rows, template library, component log, photo index, all asset logs) and `mwi-native-files-v1` (base64 of `item_photos/` and `project-attachments/`, streamed while writing).
  - Import: V10.1 validation and messages ("Restore failed: Invalid file" / "Invalid data", confirm "Replace existing saved data with backup?", "Restored. Reloading."). A file that is not JSON shows "Restore failed: The file is not valid JSON." (V10.1 showed the browser's SyntaxError text). Read/write failures show the V10.1 Android toasts "Backup import failed: …" / "Backup export failed: …"; a successful export toasts "Backup exported." (native addition).
  - Restore runs in one transaction (all-or-nothing). Native backup → the whole database is replaced exactly (asset append-only triggers are dropped and re-created inside the transaction) and files are written back. Plain V10.1 backup → only the tables behind the keys it contains are replaced (V10.1 `setItem` semantics); templates, photos and assets stay; project attachments whose project disappears are removed with their files.
  - The API key is never exported or imported; the legacy plaintext `mwi-openai-api-key-v1` key in a V10.1 file is ignored. A restored `mwi-ai-settings-v1` updates endpoint/model/provider only.
  - After "Restored. Reloading." the app restarts its process (the `location.reload()` equivalent) so every screen reloads from the restored database.
  - Memory: the import reads the whole file; very large attachment sets need proportional RAM.
- AI API card: exact port of `loadAiSettingsFields / updateDetectedProvider / saveAiSettings / removeAiKey` (provider table, detection texts, suggested endpoint/model only while untouched, HTTPS + model checks, status lines) over the existing V10.1 SharedPreferences + AndroidKeyStore storage (`QeAi` in `logic/QeSettings.kt`).
- Length converter: same units, labels, defaults (mm → in), swap button and result text (`toLocaleString` with the device locale via ICU, `toPrecision(10)`).
- Saved Notes manager was ported in milestone 6.
- Tests: SettingsBackupTest (validation messages and writer layout; V10.1 sample → Room → identical V10.1 keys; V10.1 restore keeps native data and drops only orphan attachments; full native round-trip of every table + files with triggers restored; atomic rollback and path safety; length converter vs node; AI detection/validation).

## Feature D — duplicate the whole quotation as a wood-species option (t7u/t8u)
- What V10.1 already had: quotation options (add / "Duplicate option" = deep copy named "<name> Copy" / remove / rename / select). The selected option is `activeQuoteOption`; the on-screen items, discount and GST are that option; Export to Projects used it; the print sheet printed every option. There is no per-line duplicate button (none added).
- New "Duplicate as new option (wood species)": dialog with species, Rate/CFT, Rate/KOL. Copies every line (description, units, qty, price, images, AI original), discount and GST of the active option, sets the species on the option and its lines, names the option after the species (blank species = V10.1 "Duplicate option" behaviour) and makes it active. Notes are quotation-level in V10.1, so every option shares them.
- "Wood species" field per option and per line; both optional and only written to JSON when set (`species`), so they round-trip through autosave, Save Project snapshots and Full Backup.
- Re-pricing: V10.1 lines have no CFT/KOL data, so the link (`wood`: CFT/KOL volumes, wood cost per basis, base rates) is captured at Send to Quotation / Send this item. Duplicating with species rates re-prices linked lines: new price = old price − old wood cost + new wood cost (species rate × CFT or KOL volume for every row of that basis, group rates replaced), rounded like V10.1's automatic selling price. Labour, polish, consumables, other and profit stay in rupees. A blank rate keeps that part. Lines without a link (old quotations, typed lines) get the species label and keep an editable price; the status line counts them.
- Active option = official: marked "● Active — official total" in the new "Options & pricing" list (every option's total, discount, GST, grand total; tap to activate). Save Project stores all options with the active index; Export to Projects uses the active option (as V10.1).
- Print: only the active option by default; checkbox "Include other options as comparison appendix" (off by default, saved with the job) adds the others after a "COMPARISON APPENDIX" heading. This intentionally changes V10.1's print-all-options behaviour, per the spec.
- Tests: SpeciesOptionTest (link = estimation wood figure, re-price formula and equality with a fresh estimate, blank rates, duplication fidelity incl. images/AI text/discount/GST and untouched source, official totals and print order, JSON round-trip and omission of unset fields).

## Feature E — revisions (t9u)
- What V10.1 already did (app.js `saveProject` / `renderSavedProjects`): each job has a rootId; records `{id, rootId, revision, name, updatedAt, snapshot}` (full workspace JSON). Save Project wrote the current revision and **overwrote** an existing record with the same (rootId, revision). "Revise" loaded any revision as R(latest+1) ("Editing R{n} based on R{m}…"). "View / Print" loaded a revision read-only (Save answered "Loaded revision is protected…"). The print header reads "QUOTATION · R{n}". The list had one row per revision "Name · R{n}" with View / Print, Revise, Delete.
- New: the first save is R0 and every later Save Project appends R(latest+1) — saved revisions are never overwritten; the snapshot is the full workspace (all options, estimation, costings, notes, Job Review). `basedOn` records the revision it came from.
- Room v4 / MIGRATION_3_4 (real, non-destructive): adds `saved_projects.basedOn`, converts legacy records with an empty rootId to rootId = id, adds the (rootId, revision) index, and a `BEFORE UPDATE` trigger that RAISE(ABORT)s — saved revisions are read-only at the database level (insert and delete stay allowed; Full Backup restore uses delete + insert). The trigger is also re-applied on open.
- Saved Projects lists one card per job "Name · R{latest}" (V10.1 R style) with an R0 R1 R2… picker. "Open R{latest}" loads the latest revision, editable (saving creates the next one). "View / Print R{n}", "Print R{n}" (PDF straight from the snapshot, no loading), "Revise from R{n}" and "Delete R{n}" act on the picked revision; the picker defaults to the latest, so Print/Export default to it. Older revisions open read-only ("Only the latest revision can be edited.").
- Images: item photos are files referenced by path and shared by all revisions (never copied). Deleting a revision deletes only photo files that no other revision, the workspace or an asset still references. Quotation line images stay V10.1 data URLs inside the snapshot (needed for V10.1-compatible backups), so those are stored per revision.
- Read-only enforcement is at Save and in the database; fields of a read-only revision can still be typed into on screen (as V10.1) but cannot be saved.
- Tests: RevisionsTest (numbering, grouping/latest, snapshot fidelity, shared-photo deletion rules, v3→v4 migration vs exported 4.json, read-only trigger, delete/restore still allowed).

## Feature F — item groups in Estimation (t10u)
- Each estimation card has "Select for item group"; with items selected, "Group selected items" creates "Group N" (an item can be in one group; grouping again moves it). Group sections are collapsible (▾/▸) and show Cost · Selling · Profit; "Ungroup" dissolves the group; the group name is editable (rename). Ungrouped items follow under "Ungrouped items" with their subtotal, then the grand total (Cost · Selling · Profit). With no groups the screen is exactly V10.1.
- Group Rate/CFT and Rate/KOL are defaults for member items whose own rate is blank (the item's rate wins; V10.1 row rate groups still win over both). The automatic selling price follows the cost computed with the defaults; the stored item rate stays blank.
- Group labour / polish / consumables / other are group-level lumpsums. Decision: kept at group level in the estimate and **distributed proportionally by item cost onto the quotation lines** at Send to Quotation: line = item selling + (group selling − Σ item selling) × item cost / Σ item cost (equal shares if every cost is 0), rounded to rupees with the difference on the largest-cost line, so the lines add up exactly to the group price. Group selling price blank = Σ item selling + group lumpsums (lumpsums at cost); filled = the group's price. "Send this item" sends the same share.
- Storage: groups live inside the workspace JSON (`itemGroups`, absent = ungrouped), so autosave, every revision snapshot (Feature E) and Full Backup carry them; existing jobs read as ungrouped with no conversion step, so no Room schema change was needed. Send to Estimation (which rebuilds the estimation list) clears the groups. The Feature D CFT/KOL link uses the group-default rates.
- Tests: ItemGroupsTest (ungrouped = V10.1 functions exactly; defaults vs item rates and row groups; totals, lumpsums, override; distribution sums/proportions/equal shares; group/move/rename/ungroup; JSON).

## Feature G — automatic square feet (notes)
- V10.1 (documented): labour per sq.ft = toFeet(labourLength) × toFeet(labourWidth) × num(labourQty) × rate, from the Labour
  card's own L/W; polish per sq.ft = (polishAreaOverride, else itemSurface = all six faces of every row × pieces) × rate.
- Item area = Σ rows toFeet(L) × toInches(W)/12 × pieces (V10.1 unit helpers). A row ticked "Vertical" uses L × thickness.
  Shown read-only (estimate header, Labour card, per-row "Area (sq.ft)" column) with "Override item area (optional)".
- Decision: per sq.ft labour uses the item area ONLY when the labour Length AND Width are both blank (× labour Quantity when
  filled, else × 1). Any job with labour L/W filled keeps the exact V10.1 formula (tested); a V10.1 job with one of L/W blank
  still gives V10.1's 0. Lumpsum unchanged. Polish unchanged (V10.1 surface + override) — not switched to the L×W area, to
  keep V10.1 math. ASSUMPTION to confirm.
- Zero/missing width → row area 0; an item without any usable row shows "n/a" (never a crash; non-finite → 0).
- Send to Quotation / Send one carry the area (2 dp, omitted when n/a) to `QuoteItem.area` (editable "Area (sq.ft, optional)");
  "Show area (sq.ft) on print" (WorkspaceState.printArea, default off) prints "Area: X sq.ft" under the description.
- New optional JSON fields only (Row.areaFace, Costing.areaOverride, QuoteItem.area, WorkspaceState.printArea): no Room change.

## Feature H — Log Calculator / Yield Tracker (notes)
- Home entry "Hoppus Log Volume" renamed "Log Calculator"; screen has tabs Hoppus (V10.1 sheet unchanged) | Yield Tracker.
- Hoppus "Save & send to Yield Tracker": raw volume (default = Hoppus total, editable), unit CFT/m³ (1 m³ = 35.3147 CFT),
  species, job type (free text + suggestions: built-ins and earlier job types), date (YYYY-MM-DD, default today), log details
  (pre-filled "Log n: L ft × G in girth = V CFT"). Saved at once; opened in the editor so output can be added later.
- Output: direct CFT, or finished sizes L ft × W in × T in × pieces (V10.1 calcRow forced to CFT). Waste: offcuts, breakage,
  other (CFT); "unaccounted" = input − output − recorded waste (shown, can be negative if over-recorded).
- Yield % = output ÷ input × 100, loss % = 100 − yield; input 0 → n/a. Averages per species / job type are volume-weighted
  (Σ out ÷ Σ in) with the simple mean shown too (ASSUMPTION: weighted is the headline figure).
- Trend: Compose Canvas line of yield % by date when 2+ entries have a yield.
- Room: `yield_entries` (MIGRATION_4_5, new table only; DB v5). Included in Full Backup (schema 5).
- Tests: QeYieldTest (ratios, rows, aggregates, Hoppus hand-off); MigrationReplayTest replays every step ≥ v3 on the
  exported schema and compares with the next exported schema.

## Feature I — Door & Window Builder (notes)
- Home › "Door & Window Builder" edits the current project (stored in the workspace JSON `doorWindows`, so it is saved with
  every revision; job wastage override `dwWastage`). Templates and Settings defaults live in kv
  (`mwi-native-dw-templates-v1`, `mwi-native-dw-defaults-v1`) and are part of the Full Backup.
- Pieces: main door 2 long + 2 short; bedroom door 2 long + 1 short; other door 2 + 2; window 2 + 2 + 1 or 2 centre supports.
  Counts are overridable on every kind (blank = default). Wood 5x3 / 11x2 / 4.5x2.5 (face width × thickness, in) or custom.
- JUTTU (default): long = height + 2 × ext; short = width − 2 × dim + 2 × ext. SCREW: short = width; long = height − 2 × dim.
  ext = structure "Juttu extension per end", else Settings default 3 in (PLACEHOLDER — confirm). dim = "Subtracted dimension",
  default = member thickness (ASSUMPTION — confirm). Jason's text contradicts itself on which piece runs through; the
  explicit rule above is implemented.
- Window centre support(s) = height − 2 × dim, no Juttu extension (ASSUMPTION).
- Wastage per cut 0 / ¼ / ½ in: structure → job → Settings (default 0); added to every cut before CFT; the finished length is
  shown next to the cut length.
- Architrave (default off): header = opening width + 2 × architrave width (1), jambs = opening height + architrave width (2);
  window: same 3 pieces + optional bottom piece = header length (ASSUMPTION — confirm the bottom piece and its length).
- Planking (doors only): side supports 4/5 in (default 5); middle = width − 2 × side; count = ceil(middle ÷ plank width);
  the last plank is trimmed to the remainder and shown as its own row; plank length = height − min(shorts, 2) × dim (inner
  height between the shorts, both styles — ASSUMPTION); thickness defaults to the frame thickness.
- Quantity multiplies every piece. CFT = cut ft × W × T ÷ 144 × qty (V10.1 calcRow; "Add to calculator" rows reproduce it
  exactly, lengths in inches). Added items carry category Doors & Windows (Feature J).
- Zero/negative lengths give a warning line instead of a piece; nothing crashes on blank input.

## Feature K — bilingual cut list (notes)
- Detail sheet: Door & Window Builder › "Print cut list" → A4 through PrintManager (PdfDocument, same as the quotation).
  Header: job (project name, else customer), date, total pieces, total CFT; per-entry summary lines; sections Frame Support,
  Planks, Architrave, Window Supports, each with header row, rows (piece + source structure, finished length after wastage =
  cut length, width, thickness, qty, species, CFT) and a section total. Rows never split across pages (whole row moves, the
  header is repeated); a section title stays with its header and first row.
- Every label is "English / ಕನ್ನಡ" with no toggle (StaticLayout wraps to two lines when needed). Kannada is ONLY on the cut
  list; the app UI and quotations stay English. "Juttu / ಜುಟ್ಟು" is kept as the trade word; Architrave = ವೊಟ್ಟುಪಟ್ಟಿ
  (vottupatti); never ಉಥುಪಟ್ಟಿ / uthupatti / vuthupatti (tested). Frame = ಚೌಕಟ್ಟು.
- Species field added to each builder structure.
- Kannada terms for a fluent speaker to check:
  - Cut List — ಕತ್ತರಿಸುವ ಪಟ್ಟಿ
  - Job — ಕೆಲಸ
  - Date — ದಿನಾಂಕ
  - Total pieces — ಒಟ್ಟು ತುಂಡುಗಳು
  - Total CFT — ಒಟ್ಟು CFT
  - Entries — ನಮೂದುಗಳು
  - Section total — ವಿಭಾಗದ ಒಟ್ಟು
  - pieces — ತುಂಡುಗಳು
  - identical sets — ಒಂದೇ ರೀತಿಯ ಸೆಟ್‌ಗಳು
  - Qty — ಸಂಖ್ಯೆ
  - No door or window structures in this project. — ಈ ಕೆಲಸದಲ್ಲಿ ಬಾಗಿಲು ಅಥವಾ ಕಿಟಕಿ ಇಲ್ಲ.
  - Piece — ತುಂಡು
  - Finished length (after wastage) — ಅಂತಿಮ ಉದ್ದ (ವ್ಯರ್ಥ ಸೇರಿ)
  - Width — ಅಗಲ
  - Thickness — ದಪ್ಪ
  - Qty — ಸಂಖ್ಯೆ
  - Species — ಮರದ ಜಾತಿ
  - Frame Support — ಚೌಕಟ್ಟು ಬೆಂಬಲ
  - Planks — ಹಲಗೆಗಳು
  - Architrave — ವೊಟ್ಟುಪಟ್ಟಿ
  - Window Supports — ಕಿಟಕಿ ಬೆಂಬಲ
  - Long (side) — ಉದ್ದ ತುಂಡು (ಬದಿ)
  - Short (top/bottom) — ಸಣ್ಣ ತುಂಡು (ಮೇಲೆ/ಕೆಳಗೆ)
  - Centre support — ಮಧ್ಯ ಬೆಂಬಲ
  - Plank — ಹಲಗೆ
  - Plank (trimmed) — ಹಲಗೆ (ಕತ್ತರಿಸಿದ)
  - Architrave header — ವೊಟ್ಟುಪಟ್ಟಿ ಮೇಲ್ಭಾಗ
  - Architrave jamb — ವೊಟ್ಟುಪಟ್ಟಿ ಬದಿ
  - Architrave bottom — ವೊಟ್ಟುಪಟ್ಟಿ ಕೆಳಭಾಗ
  - Main door — ಮುಖ್ಯ ಬಾಗಿಲು
  - Bedroom door — ಮಲಗುವ ಕೋಣೆಯ ಬಾಗಿಲು
  - Other door — ಇತರ ಬಾಗಿಲು
  - Window — ಕಿಟಕಿ
  - Screw — ಸ್ಕ್ರೂ
  - Juttu — ಜುಟ್ಟು (transliteration, not a translation)

## Feature J — estimation categories (notes)
- Estimation has tabs "Furniture (n) · ₹" | "Doors & Windows (n) · ₹". `CalcItem.category` null = Furniture (all V10.1 data,
  so no migration is needed: existing items read as Furniture); builder "Add to calculator" items carry "dw" through
  Send to Estimation. "Move to Doors & Windows / Move to Furniture" on every card (the item leaves its item group).
- Each category has default rates (Rate/CFT, Rate/KOL, Labour rate/sq.ft, Polish rate/sq.ft) in
  `WorkspaceState.categoryDefaults`; precedence item → item group → category. Consumables/other/selling stay per item
  (lumpsums are per item or per group) — ASSUMPTION.
- Category subtotal (cost · selling · profit) under each tab; the grand total and Send to Quotation cover both.
- Item groups live inside one category ("Items in a group must be in the same category.").
- Quotation lines keep their category (editable "Category" select); "Group lines by category on print" (default off) prints
  Furniture then Doors & Windows under heading rows, numbering continuous.
- A Furniture-only job with no category defaults takes exactly the V10.1 path (QeGroups.plainV101; tested).

## Feature L — job QR codes (notes)
- `saved_projects.jobUuid` (MIGRATION_5_6, DB v6): one UUID v4 per job, shared by all its revisions; existing rows are
  backfilled in SQL (the read-only revision trigger is dropped and re-created around it). The same idempotent backfill runs on
  every open, so restoring an older Full Backup also gets UUIDs. `WorkspaceState.jobUuid` carries it with the workspace.
- Payload `qe://job/<uuid>` (ZXing core 3.5.3, error correction M). Saved Projects › "QR label" shows the code and prints a
  60 mm label (QR, "Name · Rn", id) on A4 through PrintManager.
- Scan: Home › "Scan job QR" uses `play-services-code-scanner` 16.1.0 (Google's scanner UI; the app requests NO camera
  permission — merged manifest checked). The phone camera app also works: MainActivity handles `qe://job/<uuid>`
  (VIEW intent filter, launchMode singleTop) and opens the job's latest revision.
- minSdk 26 / AGP 8.7.3 compatible; R8 is off (isMinifyEnabled = false), so no keep rules needed. APK size: +1.06 MB
  (7.92 → 8.98 MB unsigned local build). Scanner needs Google Play services on the phone (graceful message otherwise).
- Scope ASSUMPTION: QR codes are for saved quotations/projects (Saved Projects); Operations › Projects & Payments rows have no QR.

## Feature M — voice input (notes)
- A 🎤 button sits next to each L / W / T and Pieces field in the Timber Calculator. It opens the system speech recogniser
  (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, en-IN, free form, up to 5 alternatives); the app itself requests no
  RECORD_AUDIO permission. No recogniser on the phone → "Voice input is not available…" alert; nothing understood →
  "Couldn't read a measurement…" alert with what was heard. Fields are never cleared on failure.
- Parser `logic/QeVoice.kt` (pure Kotlin, tested): digits or number words up to the hundreds ("twenty five", "one hundred
  twenty"), "point" decimals, "and a half / quarter / three quarters", "½". "by" / "x" / "into" split values into
  consecutive fields starting at the tapped one (L → W → T → Pieces). "7 feet 4 inches" (or "seven feet four") becomes
  88 in, with the unit select set to "in"; a spoken unit (ft, in, cm, mm, m) sets that field's unit. "quantity 4" / "qty 4" /
  "4 pieces" fill Pieces. Values only; rows are not added automatically.
- ASSUMPTION: Pieces has no unit, so a spoken unit there is ignored; values beyond Pieces are dropped.

## Feature N — duplicate job (notes)
- Saved Projects › "Duplicate Rn" copies that revision's whole snapshot (calculator, estimation, costings, every quote
  option, customer, quote, actuals, groups, categories, door/window structures, quotation images) into a NEW job:
  "<name> (copy)", new project id, new job UUID (so a new QR), revision R0, no revision history. V10.1 has no job
  duplicate; the closest it has is "Duplicate option" inside a quotation, which stays unchanged.
- Item photos are copied to new files under item_photos/ (registered in item_photos), so deleting either job never
  removes the other's photos. Quotation images are embedded data and are copied with the snapshot.
- The copy is saved straight into Saved Projects (top of the list); the open workspace is not touched. Template-library
  logs are not copied (they belong to the original job).
- ASSUMPTION: actual costs (materials, labour hours) are copied too, as part of "the whole job".

## Feature O — client database (notes)
- Room v7 (MIGRATION_6_7): `clients` (id, name, phone, address, notes, createdAt) and `jobs` (jobUuid PK, clientId) —
  mutable per-job metadata keyed by the Feature L job UUID, kept apart from the append-only revision rows. Both tables
  are in the Full Backup (native schema 7). Older backups restore with no clients.
- Home › "Clients": search, add / edit / delete (deleting a client unlinks its jobs; jobs stay saved), past jobs
  (latest revision of each linked job, with "Open") and Total spend.
- Quotation: a "Client" picker above the customer fields (screen only, not printed). Picking fills Customer name and
  Customer location (= client address) and links the job; "Save as client" creates a client from the customer fields.
  The link is written to `jobs` on Save Project, and travels in the snapshot (`clientId`) so duplicates keep it.
- ASSUMPTION: "Total spend" = sum of the quotation grand totals (incl. GST, active option) of the latest revision of
  each linked job; payments in Operations are not used. Phone needs at least 6 digits when given.

## Feature J — estimation categories (notes)
- Estimation has Furniture | Doors & Windows tabs (item count + selling subtotal on each tab). Items without a category (every
  V10.1 item and every calculator item) are Furniture; "Add to calculator" from the builder tags Doors & Windows and the tag
  survives Send to Estimation. "Move to …" on each card moves an item (it leaves its item group).
- Each category has default rates (Rate/CFT, Rate/KOL, labour rate/sq.ft, polish rate/sq.ft) used when the item — and its
  group — leaves the rate blank. Consumables, other and selling price stay per item (V10.1) — ASSUMPTION: category-level
  defaults for those were not needed because lumpsums already live on items/groups.
- Category subtotal (cost / selling / profit) under each tab; the grand total covers both and Send to Quotation sends all.
- Item groups (F) live within one category ("Items in a group must be in the same category.").
- A Furniture-only job without category defaults goes through the exact V10.1 path (QeGroups.plainV101; tested).
- Migration: none needed — the category is an optional JSON field (null = Furniture) in the workspace / saved snapshots.
- Quotation lines keep their category (editable select); "Group lines by category on print" (default off) prints
  Furniture then Doors & Windows under heading rows; numbering continues across headings.
