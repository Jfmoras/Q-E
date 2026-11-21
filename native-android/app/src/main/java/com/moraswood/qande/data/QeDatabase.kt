package com.moraswood.qande.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyValueDao {
    @Query("SELECT json FROM kv_store WHERE `key` = :key")
    suspend fun get(key: String): String?
    @Query("SELECT * FROM kv_store")
    suspend fun all(): List<KeyValueEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: KeyValueEntity)
    @Query("DELETE FROM kv_store WHERE `key` = :key")
    suspend fun remove(key: String)
    @Query("DELETE FROM kv_store")
    suspend fun clear()
}

@Dao
interface SavedProjectDao {
    @Query("SELECT * FROM saved_projects ORDER BY position")
    fun observeAll(): Flow<List<SavedProjectEntity>>
    @Query("SELECT * FROM saved_projects ORDER BY position")
    suspend fun all(): List<SavedProjectEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(items: List<SavedProjectEntity>)
    @Query("DELETE FROM saved_projects")
    suspend fun clear()
    /** Feature E: every save appends a new revision row (saved revisions are never updated; a trigger enforces it). */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SavedProjectEntity)
    @Query("SELECT COALESCE(MIN(position), 0) FROM saved_projects")
    suspend fun minPosition(): Int
    @Query("DELETE FROM saved_projects WHERE id = :id")
    suspend fun deleteById(id: String)
    /** Replaces the whole list, like V10.1 `writeSaved(list)`. */
    @Transaction
    suspend fun replaceAll(items: List<SavedProjectEntity>) { clear(); putAll(items) }
}

@Dao
interface SavedNoteDao {
    @Query("SELECT * FROM saved_notes ORDER BY position")
    fun observeAll(): Flow<List<SavedNoteEntity>>
    @Query("SELECT * FROM saved_notes ORDER BY position")
    suspend fun all(): List<SavedNoteEntity>
    @Insert
    suspend fun insertAll(items: List<SavedNoteEntity>)
    @Query("DELETE FROM saved_notes")
    suspend fun clear()
    /** Replaces the whole list, like V10.1 `writeSavedNotes(list)`. */
    @Transaction
    suspend fun replaceAll(notes: List<String>) {
        clear(); insertAll(notes.mapIndexed { i, text -> SavedNoteEntity(position = i, text = text) })
    }
}

@Dao
interface OperationsDao {
    @Query("SELECT * FROM ops_projects ORDER BY position")
    fun observeProjects(): Flow<List<OpsProjectEntity>>
    @Query("SELECT * FROM ops_projects ORDER BY position")
    suspend fun projects(): List<OpsProjectEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProject(project: OpsProjectEntity)
    @Delete
    suspend fun deleteProject(project: OpsProjectEntity)

    @Query("SELECT * FROM ops_project_entries ORDER BY rowId")
    fun observeEntries(): Flow<List<OpsProjectEntryEntity>>
    @Query("SELECT * FROM ops_project_entries ORDER BY rowId")
    suspend fun entries(): List<OpsProjectEntryEntity>
    @Insert
    suspend fun addEntry(entry: OpsProjectEntryEntity)

    @Query("SELECT * FROM ops_attachments WHERE projectId = :projectId ORDER BY at")
    fun observeAttachments(projectId: String): Flow<List<OpsAttachmentEntity>>
    @Query("SELECT * FROM ops_attachments WHERE projectId = :projectId ORDER BY at")
    suspend fun attachments(projectId: String): List<OpsAttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAttachment(attachment: OpsAttachmentEntity)
    @Query("DELETE FROM ops_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("SELECT * FROM ops_materials ORDER BY position")
    fun observeMaterials(): Flow<List<OpsMaterialEntity>>
    @Query("SELECT * FROM ops_materials ORDER BY position")
    suspend fun materials(): List<OpsMaterialEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMaterial(material: OpsMaterialEntity)

    @Query("SELECT * FROM ops_movements ORDER BY position")
    fun observeMovements(): Flow<List<OpsMovementEntity>>
    @Query("SELECT * FROM ops_movements ORDER BY position")
    suspend fun movements(): List<OpsMovementEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addMovement(movement: OpsMovementEntity)

    @Query("SELECT * FROM ops_machines ORDER BY position")
    fun observeMachines(): Flow<List<OpsMachineEntity>>
    @Query("SELECT * FROM ops_machines ORDER BY position")
    suspend fun machines(): List<OpsMachineEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addMachine(machine: OpsMachineEntity)
    /** UPDATE, never REPLACE: a REPLACE would delete the row and cascade away its history. */
    @Query("UPDATE ops_machines SET status = :status, worker = :worker WHERE id = :id")
    suspend fun updateMachine(id: String, status: String, worker: String)
    @Query("SELECT * FROM ops_machine_history ORDER BY rowId")
    fun observeMachineHistory(): Flow<List<OpsMachineHistoryEntity>>
    @Query("SELECT * FROM ops_machine_history ORDER BY rowId")
    suspend fun machineHistory(): List<OpsMachineHistoryEntity>
    @Insert
    suspend fun addMachineHistory(entry: OpsMachineHistoryEntity)

    @Query("SELECT * FROM ops_attachments ORDER BY at, id")
    fun observeAllAttachments(): Flow<List<OpsAttachmentEntity>>
    @Query("SELECT * FROM ops_attachments WHERE id = :id")
    suspend fun attachment(id: String): OpsAttachmentEntity?
    @Query("DELETE FROM ops_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT * FROM ops_audit ORDER BY rowId")
    suspend fun audit(): List<OpsAuditEntity>
    @Insert
    suspend fun addAudit(entry: OpsAuditEntity)
}

@Database(
    entities = [
        KeyValueEntity::class, SavedProjectEntity::class, SavedNoteEntity::class,
        OpsProjectEntity::class, OpsProjectEntryEntity::class, OpsAttachmentEntity::class,
        OpsMaterialEntity::class, OpsMovementEntity::class, OpsMachineEntity::class,
        OpsMachineHistoryEntity::class, OpsAuditEntity::class,
        JobComponentLogEntity::class, TemplateEntity::class, TemplateRowEntity::class, ItemPhotoEntity::class,
        AssetEntity::class, AssetIssueEntity::class, AssetReturnEntity::class, AssetCorrectionEntity::class, AssetDecisionEntity::class,
        YieldEntryEntity::class, ClientEntity::class, JobMetaEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class QeDatabase : RoomDatabase() {
    abstract fun keyValues(): KeyValueDao
    abstract fun savedProjects(): SavedProjectDao
    abstract fun savedNotes(): SavedNoteDao
    abstract fun operations(): OperationsDao
    abstract fun templates(): TemplateDao
    abstract fun itemPhotos(): ItemPhotoDao
    abstract fun assets(): AssetDao
    abstract fun yields(): YieldDao
    abstract fun clients(): ClientDao

    companion object {
        @Volatile private var instance: QeDatabase? = null
        fun get(context: Context): QeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, QeDatabase::class.java, "mwi-qe.db")
                .addMigrations(QeMigrations.MIGRATION_1_2, QeMigrations.MIGRATION_2_3, QeMigrations.MIGRATION_3_4, QeMigrations.MIGRATION_4_5, QeMigrations.MIGRATION_5_6, QeMigrations.MIGRATION_6_7, QeMigrations.MIGRATION_7_8)
                .addCallback(object : RoomDatabase.Callback() {
                    // Fresh installs get the append-only triggers too (IF NOT EXISTS: also self-heals).
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) { (QeMigrations.JOB_UUID_BACKFILL + QeMigrations.ASSET_TRIGGERS + QeMigrations.REVISION_TRIGGERS).forEach(db::execSQL) }
                })
                .build().also { instance = it }
        }
    }
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM job_component_log ORDER BY jobKey, itemIndex, rowIndex")
    suspend fun allLogs(): List<JobComponentLogEntity>
    @Query("DELETE FROM job_component_log WHERE itemIndex = :itemIndex AND jobKey IN (:jobKeys)")
    suspend fun deleteLogs(jobKeys: List<String>, itemIndex: Int)
    @Query("DELETE FROM job_component_log WHERE jobKey IN (:jobKeys) AND itemIndex >= :fromIndex")
    suspend fun deleteLogsFrom(jobKeys: List<String>, fromIndex: Int)
    /** Deletes item indexes in [fromIndex, toIndex). */
    /** Template library: logs written under the autosave session move to the project id on Save Project. */
    @Query("UPDATE job_component_log SET jobKey = :to WHERE jobKey = :from")
    suspend fun rekeyLogs(from: String, to: String)
    @Query("DELETE FROM job_component_log WHERE jobKey IN (:jobKeys) AND itemIndex >= :fromIndex AND itemIndex < :toIndex")
    suspend fun deleteLogsRange(jobKeys: List<String>, fromIndex: Int, toIndex: Int)
    @Insert
    suspend fun insertLogs(rows: List<JobComponentLogEntity>)
    /** Replaces the log of one calculator item (silent logging). */
    @Transaction
    suspend fun replaceItemLog(jobKeys: List<String>, itemIndex: Int, rows: List<JobComponentLogEntity>) {
        deleteLogs(jobKeys, itemIndex)
        if (rows.isNotEmpty()) insertLogs(rows)
    }

    @Query("SELECT * FROM templates ORDER BY createdAt DESC, id DESC")
    fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM templates ORDER BY createdAt DESC, id DESC")
    suspend fun templates(): List<TemplateEntity>
    @Query("SELECT * FROM template_rows ORDER BY templateId, position")
    fun observeTemplateRows(): Flow<List<TemplateRowEntity>>
    @Query("SELECT * FROM template_rows WHERE templateId = :templateId ORDER BY position")
    suspend fun templateRows(templateId: Long): List<TemplateRowEntity>
    @Insert
    suspend fun insertTemplate(template: TemplateEntity): Long
    @Insert
    suspend fun insertTemplateRows(rows: List<TemplateRowEntity>)
    @Transaction
    suspend fun saveTemplate(template: TemplateEntity, rows: List<TemplateRowEntity>): Long {
        val id = insertTemplate(template)
        insertTemplateRows(rows.map { it.copy(templateId = id) })
        return id
    }
    @Query("DELETE FROM template_rows WHERE templateId = :id")
    suspend fun deleteTemplateRows(id: Long)
    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplateOnly(id: Long)
    /** Rows are removed explicitly as well as by the FK cascade. */
    @Transaction
    suspend fun deleteTemplate(id: Long) { deleteTemplateRows(id); deleteTemplateOnly(id) }
}

@Dao
interface ItemPhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(photo: ItemPhotoEntity)
    @Query("DELETE FROM item_photos WHERE fileName = :fileName")
    suspend fun delete(fileName: String)
    @Query("SELECT * FROM item_photos")
    suspend fun all(): List<ItemPhotoEntity>
}
