package com.moraswood.qande.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room tables mirroring the V10.1 localStorage / IndexedDB shapes (V10.1-inventory.md §3).
 * - Workspace documents (autosave, quick calculator, hoppus) are kept as their exact V10.1 JSON
 *   in [KeyValueEntity] so a V10.1 backup round-trips byte-for-byte compatible.
 * - Saved project revisions, saved notes and all Operations data are relational.
 */

/** `mwi-autosave-v1`, `mwi-quick-calc-v1`, `mwi-hoppus-v1`, `mwi-ai-settings-v1` (without key) ... */
@Entity(tableName = "kv_store")
data class KeyValueEntity(
    @PrimaryKey val key: String,
    val json: String,
)

/** One element of `mwi-projects-v1`: `{id, rootId, revision, name, updatedAt, snapshot}`. */
@Entity(tableName = "saved_projects", indices = [Index("rootId"), Index(value = ["rootId", "revision"]), Index("jobUuid")])
data class SavedProjectEntity(
    @PrimaryKey val id: String,
    val rootId: String,
    val revision: Int,
    val name: String,
    val updatedAt: String,
    /** WorkspaceState JSON exactly as V10.1 stored it. */
    val snapshotJson: String,
    /** Order in the V10.1 array (0 = newest, list.unshift). New revisions get min(position) − 1. */
    val position: Int,
    /** Feature E (v4): revision this one was created from (null = first save or a pre-v4 record). */
    val basedOn: Int? = null,
    /** Feature L (v6): job UUID shared by every revision of a job; QR payload `qe://job/<uuid>`. */
    @androidx.room.ColumnInfo(defaultValue = "''") val jobUuid: String = "",
)

/** `mwi-saved-notes-v1` array element. */
@Entity(tableName = "saved_notes")
data class SavedNoteEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val position: Int,
    val text: String,
)

/** operations `projects[]` : `{id,name,customer,original,status,notes,created}`. */
@Entity(tableName = "ops_projects")
data class OpsProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val customer: String,
    val original: Double,
    val status: String = "Saved",
    val notes: String = "",
    val created: String,
    val position: Int,
)

/** `payments[]`, `revisions[]`, `expenses[]` of an operations project: `{amount,note,at}`. */
@Entity(
    tableName = "ops_project_entries",
    foreignKeys = [ForeignKey(entity = OpsProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class OpsProjectEntryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val projectId: String,
    /** "payment" | "revision" | "expense" */
    val kind: String,
    val amount: Double,
    val note: String,
    val at: String,
)

/** IndexedDB `mwi-project-files-v1/files`: `{id,projectId,name,type,size,at,blob}`; blob kept as a private file. */
@Entity(
    tableName = "ops_attachments",
    foreignKeys = [ForeignKey(entity = OpsProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class OpsAttachmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val type: String,
    val size: Long,
    val at: String,
    /** File name under filesDir/project-attachments/ */
    val internalFileName: String,
)

/** operations `materials[]`: `{id,name,unit,category,rate,min}`. */
@Entity(tableName = "ops_materials")
data class OpsMaterialEntity(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String,
    val category: String,
    val rate: Double,
    val min: Double,
    val position: Int,
)

/** operations `movements[]`: `{id,materialId,qty,type,worker,projectId,rate,at}` (append-only). */
@Entity(tableName = "ops_movements", indices = [Index("materialId"), Index("projectId")])
data class OpsMovementEntity(
    @PrimaryKey val id: String,
    val materialId: String,
    val qty: Double,
    /** "purchase" | "issue" | "return" */
    val type: String,
    val worker: String,
    val projectId: String,
    val rate: Double,
    val at: String,
    val position: Int,
)

/** operations `machines[]`: `{id:'MWI-0001',name,serial,worker,status,history}`. */
@Entity(tableName = "ops_machines")
data class OpsMachineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serial: String,
    val worker: String,
    val status: String = "Available",
    val position: Int,
)

/** machine `history[]`: `{at,status,worker}`. */
@Entity(
    tableName = "ops_machine_history",
    foreignKeys = [ForeignKey(entity = OpsMachineEntity::class, parentColumns = ["id"], childColumns = ["machineId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("machineId")],
)
data class OpsMachineHistoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val machineId: String,
    val at: String,
    val status: String,
    val worker: String,
)

/** operations `audit[]`: `{at,type,id,value}` or `{at,type,id,name}`. */
@Entity(tableName = "ops_audit")
data class OpsAuditEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val at: String,
    val type: String,
    val refId: String,
    val value: String? = null,
    val name: String? = null,
)

/* ---------- Schema v2 (native additions: template library + item photos) ---------- */

/**
 * Template library: one row per calculator component, silently logged while the owner edits.
 * A "job" = one calculator item of one project/session: (jobKey, itemIndex).
 * jobKey = WorkspaceState.projectId once the project is saved, otherwise the autosave session id.
 */
@Entity(tableName = "job_component_log", indices = [Index(value = ["jobKey", "itemIndex"]), Index("furnitureType")])
data class JobComponentLogEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val jobKey: String,
    val itemIndex: Int,
    val rowIndex: Int,
    /** Normalised furniture type (TemplateMatcher.normalizeType). */
    val furnitureType: String,
    /** Label as typed (tag, or item name when no tag). */
    val typeLabel: String,
    val l: String, val lUnit: String,
    val w: String, val wUnit: String,
    val t: String, val tUnit: String,
    val q: String,
    val mode: String,
    /** Converted values used for matching (V10.1 toFeet / toInches). */
    val lengthFt: Double,
    val widthIn: Double,
    val thicknessIn: Double,
    val qty: Double,
    val loggedAt: String,
)

/** A saved template ("Save as template"), tagged by furniture type. */
@Entity(tableName = "templates", indices = [Index("furnitureType")])
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val furnitureType: String,
    val createdAt: String,
    val sourceJobKey: String,
)

@Entity(
    tableName = "template_rows",
    foreignKeys = [ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateId")],
)
data class TemplateRowEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val templateId: Long,
    val position: Int,
    val l: String, val lUnit: String,
    val w: String, val wUnit: String,
    val t: String, val tUnit: String,
    val q: String,
    val mode: String,
)

/** Registry of item photos stored in filesDir/item_photos/ (CalcItem.photo holds [fileName]). */
@Entity(tableName = "item_photos")
data class ItemPhotoEntity(
    @PrimaryKey val fileName: String,
    val createdAt: String,
    val sizeBytes: Long,
)
