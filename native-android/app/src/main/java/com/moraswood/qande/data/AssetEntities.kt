package com.moraswood.qande.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/*
 * Schema v3 (native addition, Feature C): asset registry + append-only issue / return / correction logs.
 * The four log tables are insert-only: AssetDao has no @Update/@Delete for them and the database carries
 * BEFORE UPDATE / BEFORE DELETE triggers that RAISE(ABORT) (QeMigrations.ASSET_TRIGGERS).
 * Dates are ISO yyyy-MM-dd (device local date); *At columns are ISO instants.
 */

@Entity(tableName = "assets")
data class AssetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** machine | tool | accessory */
    val category: String,
    val purchaseDate: String,
    val cost: Double,
    val serial: String,
    /** File name in filesDir/item_photos (shared photo store), or null. */
    val photoFile: String?,
    val createdAt: String,
)

@Entity(
    tableName = "asset_issues",
    foreignKeys = [ForeignKey(entity = AssetEntity::class, parentColumns = ["id"], childColumns = ["assetId"])],
    indices = [Index("assetId")],
)
data class AssetIssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val worker: String,
    val issuedOn: String,
    val dueBack: String?,
    val notes: String,
    val createdAt: String,
)

@Entity(
    tableName = "asset_returns",
    foreignKeys = [ForeignKey(entity = AssetIssueEntity::class, parentColumns = ["id"], childColumns = ["issueId"])],
    indices = [Index("issueId")],
)
data class AssetReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issueId: Long,
    val returnedOn: String,
    val condition: String,
    val createdAt: String,
)

/**
 * A correction request referencing an earlier issue or return entry. Null fields = unchanged.
 * It changes nothing until an "approved" decision exists for it.
 */
@Entity(tableName = "asset_corrections", indices = [Index("targetType", "targetId")])
data class AssetCorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** issue | return */
    val targetType: String,
    val targetId: Long,
    val worker: String? = null,
    val issuedOn: String? = null,
    val dueBack: String? = null,
    val clearDueBack: Boolean = false,
    val notes: String? = null,
    val returnedOn: String? = null,
    val condition: String? = null,
    /** Approved void = the referenced entry is ignored (kept in history, struck out). */
    val voidEntry: Boolean = false,
    val reason: String,
    val requestedAt: String,
)

/** Owner decision log (append-only, at most one decision per request). */
@Entity(
    tableName = "asset_correction_decisions",
    foreignKeys = [ForeignKey(entity = AssetCorrectionEntity::class, parentColumns = ["id"], childColumns = ["correctionId"])],
    indices = [Index(value = ["correctionId"], unique = true)],
)
data class AssetDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val correctionId: Long,
    /** approved | rejected */
    val decision: String,
    val decidedAt: String,
)

/** Insert-only access to the asset logs: there is deliberately no update or delete method here. */
@Dao
interface AssetDao {
    @Query("SELECT * FROM assets ORDER BY id") fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM asset_issues ORDER BY id") fun observeIssues(): Flow<List<AssetIssueEntity>>
    @Query("SELECT * FROM asset_returns ORDER BY id") fun observeReturns(): Flow<List<AssetReturnEntity>>
    @Query("SELECT * FROM asset_corrections ORDER BY id") fun observeCorrections(): Flow<List<AssetCorrectionEntity>>
    @Query("SELECT * FROM asset_correction_decisions ORDER BY id") fun observeDecisions(): Flow<List<AssetDecisionEntity>>

    @Query("SELECT * FROM assets ORDER BY id") suspend fun assets(): List<AssetEntity>
    @Query("SELECT * FROM asset_issues ORDER BY id") suspend fun issues(): List<AssetIssueEntity>
    @Query("SELECT * FROM asset_returns ORDER BY id") suspend fun returns(): List<AssetReturnEntity>
    @Query("SELECT * FROM asset_corrections ORDER BY id") suspend fun corrections(): List<AssetCorrectionEntity>
    @Query("SELECT * FROM asset_correction_decisions ORDER BY id") suspend fun decisions(): List<AssetDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addAsset(a: AssetEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addIssue(e: AssetIssueEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addReturn(e: AssetReturnEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addCorrection(e: AssetCorrectionEntity): Long
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun addDecision(e: AssetDecisionEntity): Long
}
