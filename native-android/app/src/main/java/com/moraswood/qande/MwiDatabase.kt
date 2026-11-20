package com.moraswood.qande

import androidx.room.*

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val customer: String,
    val agreedValue: Double,
    val status: String = "Saved",
    val notes: String = ""
)

@Entity(tableName = "attachments", foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)], indices = [Index("projectId")])
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val displayName: String,
    val mimeType: String,
    val internalFileName: String
)

@Entity(tableName = "project_entries", indices = [Index("projectId")])
data class ProjectEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val recordedAt: String
)

@Dao
interface MwiDao {
    @Query("SELECT * FROM projects ORDER BY name")
    suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM attachments WHERE projectId = :projectId")
    suspend fun attachments(projectId: String): List<AttachmentEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProject(project: ProjectEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAttachment(attachment: AttachmentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEntry(entry: ProjectEntryEntity)
    @Query("SELECT * FROM project_entries WHERE projectId = :projectId")
    suspend fun entries(projectId: String): List<ProjectEntryEntity>
}

@Database(entities = [ProjectEntity::class, AttachmentEntity::class, ProjectEntryEntity::class], version = 1, exportSchema = true)
abstract class MwiDatabase : RoomDatabase() {
    abstract fun dao(): MwiDao
}
