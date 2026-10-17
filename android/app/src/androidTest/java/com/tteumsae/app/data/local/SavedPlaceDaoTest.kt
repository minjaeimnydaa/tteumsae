package com.tteumsae.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedPlaceDaoTest {
    private lateinit var database: TteumsaeDatabase
    private lateinit var dao: SavedPlaceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TteumsaeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.savedPlaceDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observed_places_are_owner_isolated_latest_first_and_hide_tombstones() = runBlocking {
        dao.upsert(entity(scope = GUEST_SCOPE, id = "old", savedAt = 10))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "recent", savedAt = 30))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "removed", savedAt = 40, desiredSaved = false))
        dao.upsert(entity(scope = "USER:one", id = "private", savedAt = 50))

        assertEquals(
            listOf("recent", "old"),
            dao.observeSaved(GUEST_SCOPE).first().map { it.placeId },
        )
    }

    @Test
    fun revisions_and_dirty_rows_are_scoped_and_ordered() = runBlocking {
        assertEquals(1, dao.nextRevision(GUEST_SCOPE))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "late", revision = 5, syncState = SavedPlaceSyncState.DIRTY))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "first", revision = 2, syncState = SavedPlaceSyncState.DIRTY))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "synced", revision = 4))
        dao.upsert(entity(scope = "USER:one", id = "other", revision = 20, syncState = SavedPlaceSyncState.DIRTY))

        assertEquals(6, dao.nextRevision(GUEST_SCOPE))
        assertEquals(
            listOf("first", "late"),
            dao.dirty(GUEST_SCOPE).map { it.placeId },
        )
    }

    @Test
    fun mark_all_unsaved_and_delete_scope_do_not_touch_other_owners() = runBlocking {
        dao.upsert(entity(scope = GUEST_SCOPE, id = "guest-a", revision = 1))
        dao.upsert(entity(scope = GUEST_SCOPE, id = "guest-b", revision = 2))
        dao.upsert(entity(scope = "USER:one", id = "account", revision = 1))

        dao.markAllUnsaved(GUEST_SCOPE, revision = 3)

        assertEquals(emptyList<SavedPlaceEntity>(), dao.observeSaved(GUEST_SCOPE).first())
        assertFalse(dao.find(GUEST_SCOPE, "guest-a")!!.desiredSaved)
        assertEquals(3, dao.find(GUEST_SCOPE, "guest-a")!!.localRevision)
        assertNotNull(dao.find("USER:one", "account"))

        dao.deleteScope(GUEST_SCOPE)

        assertNull(dao.find(GUEST_SCOPE, "guest-a"))
        assertNotNull(dao.find("USER:one", "account"))
    }

    private fun entity(
        scope: String,
        id: String,
        savedAt: Long = 10,
        revision: Long = 1,
        desiredSaved: Boolean = true,
        syncState: SavedPlaceSyncState = SavedPlaceSyncState.SYNCED,
    ) = SavedPlaceEntity(
        ownerScope = scope,
        placeId = id,
        placeSnapshotJson = "{}",
        snapshotState = SavedPlaceSnapshotState.READY.name,
        desiredSaved = desiredSaved,
        savedAtMillis = savedAt,
        localRevision = revision,
        remoteUpdatedAt = null,
        syncState = syncState.name,
    )
}
