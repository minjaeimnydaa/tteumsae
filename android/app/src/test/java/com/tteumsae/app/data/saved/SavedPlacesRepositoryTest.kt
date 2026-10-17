package com.tteumsae.app.data.saved

import com.tteumsae.app.data.local.DatabaseTransactionRunner
import com.tteumsae.app.data.local.GUEST_SCOPE
import com.tteumsae.app.data.local.SavedPlaceDao
import com.tteumsae.app.data.local.SavedPlaceEntity
import com.tteumsae.app.data.local.SavedPlaceSnapshotState
import com.tteumsae.app.data.local.SavedPlaceSyncState
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import com.tteumsae.app.domain.saved.SavedPlace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacesRepositoryTest {
    @Test
    fun guest_toggle_creates_then_tombstones() = runBlocking {
        val dao = FakeSavedPlaceDao()
        val repository = RoomSavedPlacesRepository(dao, DirectTransactionRunner())

        repository.toggleGuest(place(), nowMillis = 100L)

        val created = dao.find(GUEST_SCOPE, "place-1")!!
        assertTrue(created.desiredSaved)
        assertEquals(100L, created.savedAtMillis)
        assertEquals(SavedPlaceSyncState.SYNCED.name, created.syncState)
        assertEquals(SavedPlaceSnapshotState.READY.name, created.snapshotState)

        repository.toggleGuest(place(), nowMillis = 200L)

        val tombstone = dao.find(GUEST_SCOPE, "place-1")!!
        assertFalse(tombstone.desiredSaved)
        assertEquals(100L, tombstone.savedAtMillis)
        assertEquals(2L, tombstone.localRevision)
        assertEquals(SavedPlaceSyncState.SYNCED.name, tombstone.syncState)
    }

    @Test
    fun restore_preserves_the_original_saved_timestamp() = runBlocking {
        val dao = FakeSavedPlaceDao()
        val repository = RoomSavedPlacesRepository(dao, DirectTransactionRunner())
        val savedPlace = SavedPlace(place(), savedAtMillis = 321L)

        repository.restoreGuest(savedPlace)

        val restored = dao.find(GUEST_SCOPE, "place-1")!!
        assertTrue(restored.desiredSaved)
        assertEquals(321L, restored.savedAtMillis)
        assertEquals(SavedPlaceSyncState.SYNCED.name, restored.syncState)
    }

    @Test
    fun clear_guest_tombstones_every_saved_row() = runBlocking {
        val dao = FakeSavedPlaceDao()
        val repository = RoomSavedPlacesRepository(dao, DirectTransactionRunner())
        repository.toggleGuest(place("place-1"), nowMillis = 100L)
        repository.toggleGuest(place("place-2"), nowMillis = 200L)

        repository.clearGuest()

        assertTrue(dao.rows.filter { it.ownerScope == GUEST_SCOPE }.all { !it.desiredSaved })
        assertEquals(
            setOf(3L),
            dao.rows.filter { it.ownerScope == GUEST_SCOPE }.map { it.localRevision }.toSet(),
        )
    }

    @Test
    fun observe_saved_decodes_ready_snapshots_latest_first() = runBlocking {
        val dao = FakeSavedPlaceDao()
        val repository = RoomSavedPlacesRepository(dao, DirectTransactionRunner())
        repository.toggleGuest(place("older"), nowMillis = 100L)
        repository.toggleGuest(place("newer"), nowMillis = 200L)

        val saved = repository.observeSaved().first()

        assertEquals(listOf("newer", "older"), saved.map { it.place.id })
        assertEquals(listOf(200L, 100L), saved.map { it.savedAtMillis })
    }

    private fun place(id: String = "place-1") = PlaceCandidate(
        id = id,
        name = "테스트 장소 $id",
        category = PlaceCategory.CAFE,
        stayMinutes = 30,
        firstLegMinutes = 10,
        secondLegMinutes = 20,
        detourMinutes = 5,
        reason = "추천 이유",
        tags = listOf("커피"),
    )
}

private class DirectTransactionRunner : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

private class FakeSavedPlaceDao : SavedPlaceDao {
    private val state = MutableStateFlow<List<SavedPlaceEntity>>(emptyList())
    val rows: List<SavedPlaceEntity> get() = state.value

    override fun observeSaved(scope: String): Flow<List<SavedPlaceEntity>> =
        MutableStateFlow(savedRows(scope))

    override suspend fun find(scope: String, placeId: String): SavedPlaceEntity? =
        rows.find { it.ownerScope == scope && it.placeId == placeId }

    override suspend fun nextRevision(scope: String): Long =
        (rows.filter { it.ownerScope == scope }.maxOfOrNull { it.localRevision } ?: 0L) + 1L

    override suspend fun upsert(entity: SavedPlaceEntity) {
        state.value = rows
            .filterNot { it.ownerScope == entity.ownerScope && it.placeId == entity.placeId } + entity
    }

    override suspend fun markAllUnsaved(scope: String, revision: Long) {
        state.value = rows.map { entity ->
            if (entity.ownerScope == scope) {
                entity.copy(desiredSaved = false, localRevision = revision)
            } else {
                entity
            }
        }
    }

    override suspend fun deleteScope(scope: String) {
        state.value = rows.filterNot { it.ownerScope == scope }
    }

    override suspend fun dirty(scope: String): List<SavedPlaceEntity> = rows.filter {
        it.ownerScope == scope && it.syncState == SavedPlaceSyncState.DIRTY.name
    }

    private fun savedRows(scope: String): List<SavedPlaceEntity> = rows
        .filter { it.ownerScope == scope && it.desiredSaved }
        .sortedByDescending { it.savedAtMillis }
}
