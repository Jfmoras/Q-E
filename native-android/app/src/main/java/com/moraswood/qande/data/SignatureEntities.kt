package com.moraswood.qande.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Feature Q — a client signature captured on the quotation (DB v9). Rows are write-once: signing again makes a new row,
 * so a saved revision keeps pointing at the signature it was printed with. [png] is base64 PNG (TEXT, so Full Backup
 * carries it as a plain JSON string).
 */
@Entity(tableName = "signatures")
data class SignatureEntity(
    @PrimaryKey val id: String,
    val png: String,
    val signer: String = "",
    val signedAt: String = "",
    val jobUuid: String = "",
)

@Dao
interface SignatureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(s: SignatureEntity)
    @Query("SELECT * FROM signatures WHERE id = :id")
    suspend fun get(id: String): SignatureEntity?
}
