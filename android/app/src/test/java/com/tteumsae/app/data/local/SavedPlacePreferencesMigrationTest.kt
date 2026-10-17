package com.tteumsae.app.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPlacePreferencesMigrationTest {
    @Test
    fun empty_source_completes_without_creating_rows() = runBlocking {
        val preferences = FakeMigrationPreferences(entries = null)
        val dao = FakeMigrationDao()
        val transactionRunner = RecordingTransactionRunner()
        val migration = SavedPlacePreferencesMigration(preferences, dao, transactionRunner)

        val result = migration.migrateIfNeeded()

        assertEquals(MigrationResult.Migrated(count = 0, skipped = 0), result)
        assertTrue(preferences.complete)
        assertEquals(null, preferences.entries)
        assertTrue(dao.rows.isEmpty())
        assertEquals(1, transactionRunner.runCount)
    }

    @Test
    fun valid_legacy_rows_are_written_as_ready_guest_snapshots() = runBlocking {
        val preferences = FakeMigrationPreferences(entries = legacyEntries(validLegacyRow()))
        val dao = FakeMigrationDao()
        val migration = SavedPlacePreferencesMigration(
            preferences,
            dao,
            RecordingTransactionRunner(),
        )

        val result = migration.migrateIfNeeded()

        assertEquals(MigrationResult.Migrated(count = 1, skipped = 0), result)
        val entity = dao.rows.single()
        assertEquals(GUEST_SCOPE, entity.ownerScope)
        assertEquals("legacy-1", entity.placeId)
        assertEquals(SavedPlaceSnapshotState.READY.name, entity.snapshotState)
        assertEquals(SavedPlaceSyncState.SYNCED.name, entity.syncState)
        assertTrue(entity.desiredSaved)
        assertEquals(1_234L, entity.savedAtMillis)
        val place = SavedPlaceSnapshotCodec.decode(entity.placeSnapshotJson)
        assertNotNull(place)
        assertEquals("안목 커피거리", place!!.name)
        assertEquals(listOf("커피", "바다"), place.tags)
        assertTrue(preferences.complete)
        assertEquals(null, preferences.entries)
    }

    @Test
    fun corrupt_rows_are_skipped_without_discarding_valid_rows() = runBlocking {
        val preferences = FakeMigrationPreferences(
            entries = legacyEntries(validLegacyRow(), """{"id":"broken"}"""),
        )
        val dao = FakeMigrationDao()
        val migration = SavedPlacePreferencesMigration(
            preferences,
            dao,
            RecordingTransactionRunner(),
        )

        val result = migration.migrateIfNeeded()

        assertEquals(MigrationResult.Migrated(count = 1, skipped = 1), result)
        assertEquals(listOf("legacy-1"), dao.rows.map { it.placeId })
        assertTrue(preferences.complete)
        assertEquals(null, preferences.entries)
    }

    @Test
    fun database_failure_preserves_legacy_entries_and_does_not_complete() = runBlocking {
        val originalEntries = legacyEntries(validLegacyRow())
        val preferences = FakeMigrationPreferences(entries = originalEntries)
        val dao = FakeMigrationDao(upsertFailure = IllegalStateException("db unavailable"))
        val migration = SavedPlacePreferencesMigration(
            preferences,
            dao,
            RecordingTransactionRunner(),
        )

        val result = migration.migrateIfNeeded()

        assertTrue(result is MigrationResult.Failed)
        assertFalse(preferences.complete)
        assertEquals(originalEntries, preferences.entries)
    }

    @Test
    fun completed_migration_is_not_executed_again() = runBlocking {
        val preferences = FakeMigrationPreferences(
            entries = legacyEntries(validLegacyRow()),
            complete = true,
        )
        val dao = FakeMigrationDao()
        val transactionRunner = RecordingTransactionRunner()
        val migration = SavedPlacePreferencesMigration(preferences, dao, transactionRunner)

        val result = migration.migrateIfNeeded()

        assertEquals(MigrationResult.AlreadyComplete, result)
        assertTrue(dao.rows.isEmpty())
        assertEquals(0, transactionRunner.runCount)
    }

    @Test(expected = CancellationException::class)
    fun coroutine_cancellation_is_not_converted_to_a_migration_failure() = runBlocking {
        val migration = SavedPlacePreferencesMigration(
            preferences = FakeMigrationPreferences(entries = legacyEntries(validLegacyRow())),
            dao = FakeMigrationDao(),
            transactionRunner = object : DatabaseTransactionRunner {
                override suspend fun <T> run(block: suspend () -> T): T {
                    throw CancellationException("cancelled")
                }
            },
        )

        migration.migrateIfNeeded()
        Unit
    }

    private fun legacyEntries(vararg rows: String): String = rows.joinToString(
        prefix = "[",
        postfix = "]",
    )

    private fun validLegacyRow(): String =
        """{"id":"legacy-1","name":"안목 커피거리","category":"CAFE","stayMinutes":30,"firstLegMinutes":10,"secondLegMinutes":20,"detourMinutes":5,"reason":"추천 이유","tags":["커피","바다"],"address":"강릉시 창해로","imageUrl":"https://example.com/coffee.jpg","latitude":37.77,"longitude":128.95,"isOpen":true,"savedAtMillis":1234}"""
}

private class FakeMigrationPreferences(
    override var entries: String?,
    override var complete: Boolean = false,
) : SavedPlaceMigrationPreferences {
    override fun removeEntries(): Boolean {
        entries = null
        return true
    }

    override fun markComplete(): Boolean {
        complete = true
        return true
    }
}

private class RecordingTransactionRunner : DatabaseTransactionRunner {
    var runCount = 0

    override suspend fun <T> run(block: suspend () -> T): T {
        runCount += 1
        return block()
    }
}

private class FakeMigrationDao(
    private val upsertFailure: Throwable? = null,
) : SavedPlaceDao {
    val rows = mutableListOf<SavedPlaceEntity>()

    override fun observeSaved(scope: String): Flow<List<SavedPlaceEntity>> =
        MutableStateFlow(rows.filter { it.ownerScope == scope && it.desiredSaved })

    override suspend fun find(scope: String, placeId: String): SavedPlaceEntity? =
        rows.find { it.ownerScope == scope && it.placeId == placeId }

    override suspend fun nextRevision(scope: String): Long =
        (rows.filter { it.ownerScope == scope }.maxOfOrNull { it.localRevision } ?: 0L) + 1L

    override suspend fun upsert(entity: SavedPlaceEntity) {
        upsertFailure?.let { throw it }
        rows.removeAll { it.ownerScope == entity.ownerScope && it.placeId == entity.placeId }
        rows += entity
    }

    override suspend fun markAllUnsaved(scope: String, revision: Long) = Unit

    override suspend fun deleteScope(scope: String) = Unit

    override suspend fun dirty(scope: String): List<SavedPlaceEntity> = emptyList()
}
