package com.moraswood.qande.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Projects & Payments dashboard (DB v10). The project itself, its payments and its files stay the V10.1 operations
 * records (ops_projects / ops_project_entries / ops_attachments), so the Operations screen and the dashboard show the
 * same projects; these tables only add what V10.1 has no place for. No foreign keys: a V10.1-only restore that replaces
 * ops_projects must not cascade away comments.
 */

/** Per-project dashboard data, keyed by the operations project id. */
@Entity(tableName = "pp_projects", indices = [Index("jobUuid")])
data class PpProjectEntity(
    @PrimaryKey val projectId: String,
    val description: String = "",
    /** "running" | "finished" | "deleted" (soft delete, undoable). */
    val status: String = "running",
    /** Date the project was given / started, yyyy-MM-dd. */
    val startDate: String = "",
    /** Linked saved job (Feature L UUID + revision root), blank when the project was not confirmed from a quotation. */
    val jobUuid: String = "",
    val jobRootId: String = "",
    /** Revision the project was confirmed from. */
    val quoteRevision: Int = 0,
    /** ops_attachments id of an uploaded PDF quotation, blank = none. */
    val quotePdfId: String = "",
    val updatedAt: String = "",
)

/** Furniture items of a project (copied from the confirmed quotation / estimation). */
@Entity(tableName = "pp_items", indices = [Index("projectId")])
data class PpItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val position: Int,
    val name: String,
    val qty: String = "",
    val unit: String = "",
    val price: Double = 0.0,
    /** Estimation sizes, one row per line ("7 ft × 4 in × 3 in × 4 pcs (CFT)"). */
    val sizes: String = "",
    /** Wood used: species + CFT/KOL. */
    val wood: String = "",
)

/** Extra hardware needed (owner can add / edit / delete). */
@Entity(tableName = "pp_hardware", indices = [Index("projectId")])
data class PpHardwareEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val qty: String = "",
    val note: String = "",
    val createdAt: String = "",
)

/** Comments board: append-only (DB triggers refuse UPDATE / DELETE outside a Full Backup restore). */
@Entity(tableName = "pp_comments", indices = [Index("projectId")])
data class PpCommentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** "user" | "client" */
    val author: String,
    val text: String,
    val at: String,
)

@Dao
interface ProjectsDao {
    @Query("SELECT * FROM pp_projects") fun observeMeta(): Flow<List<PpProjectEntity>>
    @Query("SELECT * FROM pp_projects WHERE projectId = :id") suspend fun meta(id: String): PpProjectEntity?
    @Query("SELECT * FROM pp_projects WHERE jobUuid = :uuid AND jobUuid != '' LIMIT 1") suspend fun metaByJob(uuid: String): PpProjectEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putMeta(m: PpProjectEntity)

    @Query("SELECT * FROM pp_items ORDER BY projectId, position") fun observeItems(): Flow<List<PpItemEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putItems(items: List<PpItemEntity>)

    @Query("SELECT * FROM pp_hardware ORDER BY createdAt, id") fun observeHardware(): Flow<List<PpHardwareEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putHardware(h: PpHardwareEntity)
    @Query("DELETE FROM pp_hardware WHERE id = :id") suspend fun deleteHardware(id: String)

    @Query("SELECT * FROM pp_comments ORDER BY at, id") fun observeComments(): Flow<List<PpCommentEntity>>
    /** The only write on comments. */
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addComment(c: PpCommentEntity)
}
