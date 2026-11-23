package com.moraswood.qande.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Real, non-destructive Room migrations. v1 data (projects, kv) is untouched. */
object QeMigrations {
    /** v1 -> v2: template library (job_component_log, templates, template_rows) and item photo registry. */
    val MIGRATION_1_2_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `job_component_log` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jobKey` TEXT NOT NULL, `itemIndex` INTEGER NOT NULL, `rowIndex` INTEGER NOT NULL, `furnitureType` TEXT NOT NULL, `typeLabel` TEXT NOT NULL, `l` TEXT NOT NULL, `lUnit` TEXT NOT NULL, `w` TEXT NOT NULL, `wUnit` TEXT NOT NULL, `t` TEXT NOT NULL, `tUnit` TEXT NOT NULL, `q` TEXT NOT NULL, `mode` TEXT NOT NULL, `lengthFt` REAL NOT NULL, `widthIn` REAL NOT NULL, `thicknessIn` REAL NOT NULL, `qty` REAL NOT NULL, `loggedAt` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_job_component_log_jobKey_itemIndex` ON `job_component_log` (`jobKey`, `itemIndex`)",
        "CREATE INDEX IF NOT EXISTS `index_job_component_log_furnitureType` ON `job_component_log` (`furnitureType`)",
        "CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `furnitureType` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `sourceJobKey` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_templates_furnitureType` ON `templates` (`furnitureType`)",
        "CREATE TABLE IF NOT EXISTS `template_rows` (`rowId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `l` TEXT NOT NULL, `lUnit` TEXT NOT NULL, `w` TEXT NOT NULL, `wUnit` TEXT NOT NULL, `t` TEXT NOT NULL, `tUnit` TEXT NOT NULL, `q` TEXT NOT NULL, `mode` TEXT NOT NULL, FOREIGN KEY(`templateId`) REFERENCES `templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_template_rows_templateId` ON `template_rows` (`templateId`)",
        "CREATE TABLE IF NOT EXISTS `item_photos` (`fileName` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, PRIMARY KEY(`fileName`))",
    )

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_1_2_SQL.forEach(db::execSQL)
        }
    }

    /** v2 -> v3: Feature C asset registry + append-only logs (generated from schemas/3.json, checked by MigrationSchemaTest). */
    val MIGRATION_2_3_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `assets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `purchaseDate` TEXT NOT NULL, `cost` REAL NOT NULL, `serial` TEXT NOT NULL, `photoFile` TEXT, `createdAt` TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `asset_issues` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `assetId` INTEGER NOT NULL, `worker` TEXT NOT NULL, `issuedOn` TEXT NOT NULL, `dueBack` TEXT, `notes` TEXT NOT NULL, `createdAt` TEXT NOT NULL, FOREIGN KEY(`assetId`) REFERENCES `assets`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX IF NOT EXISTS `index_asset_issues_assetId` ON `asset_issues` (`assetId`)",
        "CREATE TABLE IF NOT EXISTS `asset_returns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `issueId` INTEGER NOT NULL, `returnedOn` TEXT NOT NULL, `condition` TEXT NOT NULL, `createdAt` TEXT NOT NULL, FOREIGN KEY(`issueId`) REFERENCES `asset_issues`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX IF NOT EXISTS `index_asset_returns_issueId` ON `asset_returns` (`issueId`)",
        "CREATE TABLE IF NOT EXISTS `asset_corrections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `targetType` TEXT NOT NULL, `targetId` INTEGER NOT NULL, `worker` TEXT, `issuedOn` TEXT, `dueBack` TEXT, `clearDueBack` INTEGER NOT NULL, `notes` TEXT, `returnedOn` TEXT, `condition` TEXT, `voidEntry` INTEGER NOT NULL, `reason` TEXT NOT NULL, `requestedAt` TEXT NOT NULL)",
        "CREATE INDEX IF NOT EXISTS `index_asset_corrections_targetType_targetId` ON `asset_corrections` (`targetType`, `targetId`)",
        "CREATE TABLE IF NOT EXISTS `asset_correction_decisions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `correctionId` INTEGER NOT NULL, `decision` TEXT NOT NULL, `decidedAt` TEXT NOT NULL, FOREIGN KEY(`correctionId`) REFERENCES `asset_corrections`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_asset_correction_decisions_correctionId` ON `asset_correction_decisions` (`correctionId`)",
    )

    private val APPEND_ONLY = listOf("asset_issues", "asset_returns", "asset_corrections", "asset_correction_decisions")

    /** BEFORE UPDATE / BEFORE DELETE guards: the asset logs can only grow. */
    val ASSET_TRIGGERS: List<String> = APPEND_ONLY.flatMap { t ->
        listOf("UPDATE", "DELETE").map { op ->
            "CREATE TRIGGER IF NOT EXISTS `${t}_no_${op.lowercase()}` BEFORE $op ON `$t` BEGIN SELECT RAISE(ABORT, '$t is append-only'); END"
        }
    }

    /** Lets a Full Backup restore replace the append-only logs (dropped and re-created inside one transaction). */
    val ASSET_TRIGGER_DROPS: List<String> = APPEND_ONLY.flatMap { t -> listOf("update", "delete").map { op -> "DROP TRIGGER IF EXISTS `${t}_no_$op`" } }

    /** v3 -> v4 (Feature E revisions): `basedOn` column, (rootId, revision) index, legacy empty rootId → id. */
    val MIGRATION_3_4_SQL: List<String> = listOf(
        "ALTER TABLE `saved_projects` ADD COLUMN `basedOn` INTEGER",
        "UPDATE `saved_projects` SET `rootId` = `id` WHERE `rootId` = ''",
        "CREATE INDEX IF NOT EXISTS `index_saved_projects_rootId_revision` ON `saved_projects` (`rootId`, `revision`)",
    )

    /** Saved revisions are immutable snapshots: UPDATE is refused (delete / insert stay allowed). */
    val REVISION_TRIGGERS: List<String> = listOf(
        "CREATE TRIGGER IF NOT EXISTS `saved_projects_no_update` BEFORE UPDATE ON `saved_projects` BEGIN SELECT RAISE(ABORT, 'saved revisions are read-only'); END",
    )

    /** Feature H: Yield Tracker entries (new table only). */
    val MIGRATION_4_5_SQL: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `yield_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `species` TEXT NOT NULL, `jobType` TEXT NOT NULL, `inputVolume` REAL NOT NULL, `inputUnit` TEXT NOT NULL, `outputMode` TEXT NOT NULL, `outputVolume` REAL NOT NULL, `outputRowsJson` TEXT NOT NULL, `offcuts` REAL NOT NULL, `breakage` REAL NOT NULL, `other` REAL NOT NULL, `logDetails` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
    )

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_4_5_SQL.forEach(db::execSQL) }
    }

    private const val GEN_UUID = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)), 2) || '-' || " +
        "substr('89ab', 1 + (abs(random()) % 4), 1) || substr(hex(randomblob(2)), 2) || '-' || hex(randomblob(6)))"

    /**
     * Feature L: give every job (rootId) one UUID v4, shared by all its revisions. Idempotent (only rows with '' change);
     * also run on every open so a restored older backup is backfilled. The read-only trigger is dropped around it (the
     * caller re-creates it right after).
     */
    val JOB_UUID_BACKFILL: List<String> = listOf(
        "DROP TRIGGER IF EXISTS `saved_projects_no_update`",
        "UPDATE `saved_projects` SET `jobUuid` = $GEN_UUID WHERE `jobUuid` = '' " +
            "AND rowid = (SELECT MIN(r.rowid) FROM `saved_projects` r WHERE r.`rootId` = `saved_projects`.`rootId`) " +
            "AND NOT EXISTS (SELECT 1 FROM `saved_projects` r WHERE r.`rootId` = `saved_projects`.`rootId` AND r.`jobUuid` <> '')",
        "UPDATE `saved_projects` SET `jobUuid` = (SELECT r.`jobUuid` FROM `saved_projects` r WHERE r.`rootId` = `saved_projects`.`rootId` AND r.`jobUuid` <> '' LIMIT 1) " +
            "WHERE `jobUuid` = '' AND EXISTS (SELECT 1 FROM `saved_projects` r WHERE r.`rootId` = `saved_projects`.`rootId` AND r.`jobUuid` <> '')",
    )

    val MIGRATION_5_6_SQL: List<String> get() = listOf(
        "ALTER TABLE `saved_projects` ADD COLUMN `jobUuid` TEXT NOT NULL DEFAULT ''",
        "CREATE INDEX IF NOT EXISTS `index_saved_projects_jobUuid` ON `saved_projects` (`jobUuid`)",
    ) + JOB_UUID_BACKFILL + REVISION_TRIGGERS

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_5_6_SQL.forEach(db::execSQL) }
    }

    /** Feature O: clients + per-job metadata (linked client). */
    val MIGRATION_6_7_SQL: List<String> get() = listOf(
        "CREATE TABLE IF NOT EXISTS `clients` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, `address` TEXT NOT NULL, `notes` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `jobs` (`jobUuid` TEXT NOT NULL, `clientId` TEXT, PRIMARY KEY(`jobUuid`))",
        "CREATE INDEX IF NOT EXISTS `index_jobs_clientId` ON `jobs` (`clientId`)",
    )

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_6_7_SQL.forEach(db::execSQL) }
    }

    /** Feature P: delivery schedule on the per-job metadata (ISO dates, null = none). */
    val MIGRATION_7_8_SQL: List<String> get() = listOf(
        "ALTER TABLE `jobs` ADD COLUMN `deliveryDate` TEXT",
        "ALTER TABLE `jobs` ADD COLUMN `completedAt` TEXT",
    )

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_7_8_SQL.forEach(db::execSQL) }
    }

    /** Plain SQL per step from version 3 on (used by the migration test that replays them on the exported schemas). */
    /** Feature Q: write-once client signatures (base64 PNG). Copied from schemas/9.json createSql. */
    val MIGRATION_8_9_SQL: List<String> get() = listOf(
        "CREATE TABLE IF NOT EXISTS `signatures` (`id` TEXT NOT NULL, `png` TEXT NOT NULL, `signer` TEXT NOT NULL, `signedAt` TEXT NOT NULL, `jobUuid` TEXT NOT NULL, PRIMARY KEY(`id`))",
    )

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_8_9_SQL.forEach(db::execSQL) }
    }

    /** DB v10 only: comments were append-only. Dropped by [MIGRATION_10_11] (t47u: comments are editable). */
    val COMMENT_TRIGGERS: List<String> = listOf("UPDATE", "DELETE").map { op ->
        "CREATE TRIGGER IF NOT EXISTS `pp_comments_no_${op.lowercase()}` BEFORE $op ON `pp_comments` BEGIN SELECT RAISE(ABORT, 'comments are append-only'); END"
    }
    val COMMENT_TRIGGER_DROPS: List<String> = listOf("update", "delete").map { "DROP TRIGGER IF EXISTS `pp_comments_no_$it`" }

    /** Projects & Payments dashboard tables (new tables only). Copied from schemas/10.json createSql. */
    val MIGRATION_9_10_SQL: List<String> get() = listOf(
        "CREATE TABLE IF NOT EXISTS `pp_projects` (`projectId` TEXT NOT NULL, `description` TEXT NOT NULL, `status` TEXT NOT NULL, `startDate` TEXT NOT NULL, `jobUuid` TEXT NOT NULL, `jobRootId` TEXT NOT NULL, `quoteRevision` INTEGER NOT NULL, `quotePdfId` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`projectId`))",
        "CREATE INDEX IF NOT EXISTS `index_pp_projects_jobUuid` ON `pp_projects` (`jobUuid`)",
        "CREATE TABLE IF NOT EXISTS `pp_items` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `qty` TEXT NOT NULL, `unit` TEXT NOT NULL, `price` REAL NOT NULL, `sizes` TEXT NOT NULL, `wood` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_pp_items_projectId` ON `pp_items` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `pp_hardware` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `name` TEXT NOT NULL, `qty` TEXT NOT NULL, `note` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_pp_hardware_projectId` ON `pp_hardware` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `pp_comments` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `author` TEXT NOT NULL, `text` TEXT NOT NULL, `at` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_pp_comments_projectId` ON `pp_comments` (`projectId`)",
    ) + COMMENT_TRIGGERS

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_9_10_SQL.forEach(db::execSQL) }
    }

    /**
     * t47u: comments become editable / deletable (append-only triggers dropped, editedAt added), project revisions
     * (pp_projects.revision + frozen pp_revisions). Copied from schemas/11.json createSql.
     */
    val MIGRATION_10_11_SQL: List<String> get() = COMMENT_TRIGGER_DROPS + listOf(
        "ALTER TABLE `pp_comments` ADD COLUMN `editedAt` TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE `pp_projects` ADD COLUMN `revision` INTEGER NOT NULL DEFAULT 0",
        "CREATE TABLE IF NOT EXISTS `pp_revisions` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, `revision` INTEGER NOT NULL, `total` REAL NOT NULL, `reason` TEXT NOT NULL, `snapshotJson` TEXT NOT NULL, `createdAt` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_pp_revisions_projectId` ON `pp_revisions` (`projectId`)",
    )

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) { MIGRATION_10_11_SQL.forEach(db::execSQL) }
    }

    val STEP_SQL: Map<Int, List<String>> get() = mapOf(3 to MIGRATION_3_4_SQL, 4 to MIGRATION_4_5_SQL, 5 to MIGRATION_5_6_SQL, 6 to MIGRATION_6_7_SQL, 7 to MIGRATION_7_8_SQL, 8 to MIGRATION_8_9_SQL, 9 to MIGRATION_9_10_SQL, 10 to MIGRATION_10_11_SQL)

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_SQL.forEach(db::execSQL)
            REVISION_TRIGGERS.forEach(db::execSQL)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_2_3_SQL.forEach(db::execSQL)
            ASSET_TRIGGERS.forEach(db::execSQL)
        }
    }
}
