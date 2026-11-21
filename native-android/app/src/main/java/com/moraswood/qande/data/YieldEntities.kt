package com.moraswood.qande.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Feature H — Yield Tracker entry (Log Calculator). Input = raw log volume as entered (unit "CFT" or "m3");
 * output either direct CFT or finished-size rows (`outputRowsJson`, V10.1 Row JSON, computed with the CFT helpers).
 * Waste (offcuts, breakage, other) in CFT.
 */
@Entity(tableName = "yield_entries")
data class YieldEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val species: String,
    val jobType: String,
    val inputVolume: Double,
    val inputUnit: String,
    val outputMode: String,
    val outputVolume: Double,
    val outputRowsJson: String,
    val offcuts: Double,
    val breakage: Double,
    val other: Double,
    val logDetails: String,
    val notes: String,
    val createdAt: Long,
)

@Dao
interface YieldDao {
    @Query("SELECT * FROM yield_entries ORDER BY date DESC, createdAt DESC, id DESC")
    fun observe(): Flow<List<YieldEntryEntity>>
    @Insert
    suspend fun insert(e: YieldEntryEntity): Long
    @Update
    suspend fun update(e: YieldEntryEntity)
    @Query("DELETE FROM yield_entries WHERE id = :id")
    suspend fun delete(id: Long)
}
