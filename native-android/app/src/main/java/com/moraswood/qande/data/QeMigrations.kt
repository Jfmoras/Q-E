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
}
