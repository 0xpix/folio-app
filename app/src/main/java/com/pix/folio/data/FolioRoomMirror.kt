package com.pix.folio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * v0.8 migration bridge.
 *
 * SharedPreferences remains the source of truth during the beta migration. Every refresh can
 * mirror a complete portable backup into Room, giving Folio a transactional database foothold
 * without gambling existing user history on a one-shot storage rewrite.
 */
@Entity(tableName = "folio_snapshots")
data class FolioSnapshotEntity(
    @PrimaryKey val slot: String = "latest",
    val createdAtMillis: Long,
    val payload: String,
)

@Dao
interface FolioSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: FolioSnapshotEntity)

    @Query("SELECT * FROM folio_snapshots WHERE slot = :slot LIMIT 1")
    suspend fun get(slot: String): FolioSnapshotEntity?
}

@Database(
    entities = [FolioSnapshotEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class FolioRoomDatabase : RoomDatabase() {
    abstract fun snapshots(): FolioSnapshotDao

    companion object {
        @Volatile private var instance: FolioRoomDatabase? = null

        fun get(context: Context): FolioRoomDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FolioRoomDatabase::class.java,
                "folio_v08.db",
            ).build().also { instance = it }
        }
    }
}

object FolioRoomMirror {
    suspend fun save(context: Context) {
        val payload = FolioBackup.snapshot(context)
        FolioRoomDatabase.get(context).snapshots().put(
            FolioSnapshotEntity(
                createdAtMillis = System.currentTimeMillis(),
                payload = payload,
            )
        )
    }

    suspend fun restoreLatest(context: Context): Result<Boolean> = runCatching {
        val snapshot = FolioRoomDatabase.get(context).snapshots().get("latest") ?: return@runCatching false
        FolioBackup.import(context, snapshot.payload).getOrThrow()
        true
    }
}
