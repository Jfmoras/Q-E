package com.moraswood.qande.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Feature O — a client (customer) kept on this device. */
@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: String = "",
)

/**
 * Mutable per-job metadata keyed by the job UUID (Feature L), kept apart from the append-only revision rows:
 * the linked client (Feature O, DB v7) and the delivery schedule (Feature P, DB v8: ISO dates yyyy-MM-dd).
 */
@Entity(tableName = "jobs", indices = [Index("clientId")])
data class JobMetaEntity(
    @PrimaryKey val jobUuid: String,
    val clientId: String? = null,
    val deliveryDate: String? = null,
    val completedAt: String? = null,
)

@Dao
interface ClientDao {
    @Query("SELECT * FROM clients ORDER BY name COLLATE NOCASE, id")
    fun observeClients(): Flow<List<ClientEntity>>
    @Query("SELECT * FROM jobs")
    fun observeJobs(): Flow<List<JobMetaEntity>>
    @Query("SELECT * FROM jobs WHERE jobUuid = :uuid")
    suspend fun job(uuid: String): JobMetaEntity?
    /** clients have no child rows, so REPLACE is safe here. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putClient(c: ClientEntity)
    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientRow(id: String)
    @Query("UPDATE jobs SET clientId = NULL WHERE clientId = :id")
    suspend fun unlinkClient(id: String)
    @Transaction
    suspend fun deleteClient(id: String) { unlinkClient(id); deleteClientRow(id) }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJob(j: JobMetaEntity): Long
    @Query("UPDATE jobs SET clientId = :clientId WHERE jobUuid = :uuid")
    suspend fun setJobClient(uuid: String, clientId: String?): Int
    /** Update in place (never REPLACE, which would wipe the job's other columns). */
    @Transaction
    suspend fun linkJob(uuid: String, clientId: String?) { if (setJobClient(uuid, clientId) == 0) insertJob(JobMetaEntity(uuid, clientId)) }

    /* Feature P: delivery schedule */
    @Query("UPDATE jobs SET clientId = :clientId, deliveryDate = :deliveryDate WHERE jobUuid = :uuid")
    suspend fun setJobOnSave(uuid: String, clientId: String?, deliveryDate: String?): Int
    /** Save Project: client + delivery date from the workspace; completion is kept. */
    @Transaction
    suspend fun saveJob(uuid: String, clientId: String?, deliveryDate: String?) {
        if (setJobOnSave(uuid, clientId, deliveryDate) == 0) insertJob(JobMetaEntity(uuid, clientId, deliveryDate))
    }
    @Query("UPDATE jobs SET deliveryDate = :date WHERE jobUuid = :uuid")
    suspend fun setDeliveryDateRow(uuid: String, date: String?): Int
    @Transaction
    suspend fun setDeliveryDate(uuid: String, date: String?) { if (setDeliveryDateRow(uuid, date) == 0) insertJob(JobMetaEntity(uuid, deliveryDate = date)) }
    @Query("UPDATE jobs SET completedAt = :date WHERE jobUuid = :uuid")
    suspend fun setCompletedRow(uuid: String, date: String?): Int
    @Transaction
    suspend fun setCompleted(uuid: String, date: String?) { if (setCompletedRow(uuid, date) == 0) insertJob(JobMetaEntity(uuid, completedAt = date)) }
}
