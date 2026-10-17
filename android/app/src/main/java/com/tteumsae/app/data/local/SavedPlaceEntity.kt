package com.tteumsae.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

const val GUEST_SCOPE = "GUEST"

enum class SavedPlaceSyncState {
    SYNCED,
    DIRTY,
    FAILED_AUTH,
}

enum class SavedPlaceSnapshotState {
    READY,
    MISSING,
    UNAVAILABLE,
}

@Entity(
    tableName = "saved_places",
    primaryKeys = ["owner_scope", "place_id"],
    indices = [
        Index("owner_scope"),
        Index(value = ["owner_scope", "sync_state"]),
    ],
)
data class SavedPlaceEntity(
    @ColumnInfo(name = "owner_scope") val ownerScope: String,
    @ColumnInfo(name = "place_id") val placeId: String,
    @ColumnInfo(name = "place_snapshot_json") val placeSnapshotJson: String,
    @ColumnInfo(name = "snapshot_state") val snapshotState: String,
    @ColumnInfo(name = "desired_saved") val desiredSaved: Boolean,
    @ColumnInfo(name = "saved_at_millis") val savedAtMillis: Long,
    @ColumnInfo(name = "local_revision") val localRevision: Long,
    @ColumnInfo(name = "remote_updated_at") val remoteUpdatedAt: String?,
    @ColumnInfo(name = "sync_state") val syncState: String,
)
