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

@Dao
interface PeerDao {
    @Query("SELECT * FROM PeerEntity")
    suspend fun getAllPeers(): List<PeerEntity>

    @Insert
    suspend fun insertPeer(peer: PeerEntity)
}

// Note: Requires KSP and Room compiler setup in build.gradle.kts which was added to dependencies but KSP plugin might need to be applied at root.
@Database(entities = [PeerEntity::class], version = 1)
abstract class ProtojuncDatabase : RoomDatabase() {
    abstract fun peerDao(): PeerDao
}
