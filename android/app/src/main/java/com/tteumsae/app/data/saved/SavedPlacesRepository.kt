package com.tteumsae.app.data.saved

import com.tteumsae.app.data.local.DatabaseTransactionRunner
import com.tteumsae.app.data.local.GUEST_SCOPE
import com.tteumsae.app.data.local.SavedPlaceDao
import com.tteumsae.app.data.local.SavedPlaceEntity
import com.tteumsae.app.data.local.SavedPlaceSnapshotCodec
import com.tteumsae.app.data.local.SavedPlaceSnapshotState
import com.tteumsae.app.data.local.SavedPlaceSyncState
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.saved.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SavedPlacesRepository {
    fun observeSaved(scope: String = GUEST_SCOPE): Flow<List<SavedPlace>>

    suspend fun toggleGuest(place: PlaceCandidate, nowMillis: Long)

    suspend fun restoreGuest(savedPlace: SavedPlace)

    suspend fun clearGuest()
}

class RoomSavedPlacesRepository(
    private val dao: SavedPlaceDao,
    private val transactionRunner: DatabaseTransactionRunner,
) : SavedPlacesRepository {
    override fun observeSaved(scope: String): Flow<List<SavedPlace>> =
        dao.observeSaved(scope).map { entities ->
            entities.mapNotNull { entity ->
                SavedPlaceSnapshotCodec.decode(entity.placeSnapshotJson)?.let { place ->
                    SavedPlace(place = place, savedAtMillis = entity.savedAtMillis)
                }
            }
        }

    override suspend fun toggleGuest(place: PlaceCandidate, nowMillis: Long) {
        transactionRunner.run {
            val existing = dao.find(GUEST_SCOPE, place.id)
            val shouldSave = existing?.desiredSaved != true
            dao.upsert(
                guestEntity(
                    place = place,
                    desiredSaved = shouldSave,
                    savedAtMillis = if (shouldSave) nowMillis else existing.savedAtMillis,
                    localRevision = dao.nextRevision(GUEST_SCOPE),
                ),
            )
        }
    }

    override suspend fun restoreGuest(savedPlace: SavedPlace) {
        transactionRunner.run {
            dao.upsert(
                guestEntity(
                    place = savedPlace.place,
                    desiredSaved = true,
                    savedAtMillis = savedPlace.savedAtMillis,
                    localRevision = dao.nextRevision(GUEST_SCOPE),
                ),
            )
        }
    }

    override suspend fun clearGuest() {
        transactionRunner.run {
            dao.markAllUnsaved(
                scope = GUEST_SCOPE,
                revision = dao.nextRevision(GUEST_SCOPE),
            )
        }
    }

    private fun guestEntity(
        place: PlaceCandidate,
        desiredSaved: Boolean,
        savedAtMillis: Long,
        localRevision: Long,
    ) = SavedPlaceEntity(
        ownerScope = GUEST_SCOPE,
        placeId = place.id,
        placeSnapshotJson = SavedPlaceSnapshotCodec.encode(place),
        snapshotState = SavedPlaceSnapshotState.READY.name,
        desiredSaved = desiredSaved,
        savedAtMillis = savedAtMillis,
        localRevision = localRevision,
        remoteUpdatedAt = null,
        syncState = SavedPlaceSyncState.SYNCED.name,
    )
}
