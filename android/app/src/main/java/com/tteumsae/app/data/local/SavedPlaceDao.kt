package com.tteumsae.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {
    @Query(
        "SELECT * FROM saved_places " +
            "WHERE owner_scope = :scope AND desired_saved = 1 " +
            "ORDER BY saved_at_millis DESC",
    )
    fun observeSaved(scope: String): Flow<List<SavedPlaceEntity>>

    @Query(
        "SELECT * FROM saved_places " +
            "WHERE owner_scope = :scope AND place_id = :placeId LIMIT 1",
    )
    suspend fun find(scope: String, placeId: String): SavedPlaceEntity?

    @Query(
        "SELECT COALESCE(MAX(local_revision), 0) + 1 " +
            "FROM saved_places WHERE owner_scope = :scope",
    )
    suspend fun nextRevision(scope: String): Long

    @Upsert
    suspend fun upsert(entity: SavedPlaceEntity)

    @Query(
        "UPDATE saved_places SET desired_saved = 0, local_revision = :revision " +
            "WHERE owner_scope = :scope",
    )
    suspend fun markAllUnsaved(scope: String, revision: Long)

    @Query("DELETE FROM saved_places WHERE owner_scope = :scope")
    suspend fun deleteScope(scope: String)

    @Query(
        "SELECT * FROM saved_places " +
            "WHERE owner_scope = :scope AND sync_state = 'DIRTY' " +
            "ORDER BY local_revision",
    )
    suspend fun dirty(scope: String): List<SavedPlaceEntity>
}
