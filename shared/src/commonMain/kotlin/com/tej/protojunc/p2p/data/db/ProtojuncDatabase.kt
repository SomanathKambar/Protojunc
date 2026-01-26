package com.tej.protojunc.p2p.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity
data class PeerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lastSeen: Long
)

@Entity
data class SurgicalReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val patientId: String,
    val reportContent: String,
    val timestamp: Long,
    val isSynced: Boolean = false
)

@Dao
interface PeerDao {
    @Query("SELECT * FROM PeerEntity")
    suspend fun getAllPeers(): List<PeerEntity>

    @Insert
    suspend fun insertPeer(peer: PeerEntity)
}

@Dao
interface SurgicalReportDao {
    @Query("SELECT * FROM SurgicalReportEntity WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<SurgicalReportEntity>

    @Insert
    suspend fun insertReport(report: SurgicalReportEntity)

    @Query("UPDATE SurgicalReportEntity SET isSynced = 1 WHERE id = :reportId")
    suspend fun markAsSynced(reportId: Int)
}

// Note: Requires KSP and Room compiler setup in build.gradle.kts which was added to dependencies but KSP plugin might need to be applied at root.
@Database(entities = [PeerEntity::class, SurgicalReportEntity::class], version = 2)
abstract class ProtojuncDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
    abstract fun surgicalReportDao(): SurgicalReportDao
}
