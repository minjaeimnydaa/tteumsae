package com.tteumsae.app.data.local

import android.content.Context
import androidx.room.withTransaction
import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.PlaceCategory
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

sealed interface MigrationResult {
    data object AlreadyComplete : MigrationResult

    data class Migrated(
        val count: Int,
        val skipped: Int,
    ) : MigrationResult

    data class Failed(val cause: Throwable) : MigrationResult
}

interface SavedPlaceMigrationPreferences {
    val entries: String?
    val complete: Boolean

    fun removeEntries(): Boolean

    fun markComplete(): Boolean
}

interface DatabaseTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomDatabaseTransactionRunner(
    private val database: TteumsaeDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}

class SharedPreferencesSavedPlaceMigrationPreferences(
    context: Context,
) : SavedPlaceMigrationPreferences {
    private val savedPlaces = context.getSharedPreferences(
        LEGACY_SAVED_STORAGE,
        Context.MODE_PRIVATE,
    )
    private val migration = context.getSharedPreferences(
        SAVED_PLACES_MIGRATION_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override val entries: String?
        get() = savedPlaces.getString(SAVED_PLACES_KEY, null)

    override val complete: Boolean
        get() = migration.getBoolean(SAVED_PLACES_MIGRATION_COMPLETE_KEY, false)

    override fun removeEntries(): Boolean = savedPlaces
        .edit()
        .remove(SAVED_PLACES_KEY)
        .commit()

    override fun markComplete(): Boolean = migration
        .edit()
        .putBoolean(SAVED_PLACES_MIGRATION_COMPLETE_KEY, true)
        .commit()
}

class SavedPlacePreferencesMigration(
    private val preferences: SavedPlaceMigrationPreferences,
    private val dao: SavedPlaceDao,
    private val transactionRunner: DatabaseTransactionRunner,
) {
    suspend fun migrateIfNeeded(): MigrationResult = try {
        if (preferences.complete) {
            MigrationResult.AlreadyComplete
        } else {
            migrate()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (cause: Exception) {
        MigrationResult.Failed(cause)
    }

    private suspend fun migrate(): MigrationResult {
        val decoded = decodeLegacyEntries(preferences.entries)

        transactionRunner.run {
            decoded.entries.forEach { savedPlace ->
                dao.upsert(
                    SavedPlaceEntity(
                        ownerScope = GUEST_SCOPE,
                        placeId = savedPlace.place.id,
                        placeSnapshotJson = SavedPlaceSnapshotCodec.encode(savedPlace.place),
                        snapshotState = SavedPlaceSnapshotState.READY.name,
                        desiredSaved = true,
                        savedAtMillis = savedPlace.savedAtMillis,
                        localRevision = dao.nextRevision(GUEST_SCOPE),
                        remoteUpdatedAt = null,
                        syncState = SavedPlaceSyncState.SYNCED.name,
                    ),
                )
            }
        }

        check(preferences.removeEntries()) { "기존 저장 장소 데이터를 제거하지 못했습니다." }
        check(preferences.markComplete()) { "저장 장소 이전 완료 상태를 기록하지 못했습니다." }
        return MigrationResult.Migrated(
            count = decoded.entries.size,
            skipped = decoded.skipped,
        )
    }
}

private data class DecodedLegacyEntries(
    val entries: List<LegacySavedPlace>,
    val skipped: Int,
)

private data class LegacySavedPlace(
    val place: PlaceCandidate,
    val savedAtMillis: Long,
)

private fun decodeLegacyEntries(rawEntries: String?): DecodedLegacyEntries {
    if (rawEntries == null) return DecodedLegacyEntries(emptyList(), skipped = 0)

    val array = JSONArray(rawEntries)
    val entries = mutableListOf<LegacySavedPlace>()
    var skipped = 0
    for (index in 0 until array.length()) {
        val decoded = runCatching { decodeLegacyEntry(array.getJSONObject(index)) }.getOrNull()
        if (decoded == null) {
            skipped += 1
        } else {
            entries += decoded
        }
    }
    return DecodedLegacyEntries(entries, skipped)
}

private fun decodeLegacyEntry(item: JSONObject): LegacySavedPlace {
    val tags = item.getJSONArray("tags")
    return LegacySavedPlace(
        place = PlaceCandidate(
            id = item.getString("id"),
            name = item.getString("name"),
            category = PlaceCategory.valueOf(item.getString("category")),
            stayMinutes = item.getInt("stayMinutes"),
            firstLegMinutes = item.getInt("firstLegMinutes"),
            secondLegMinutes = item.getInt("secondLegMinutes"),
            detourMinutes = item.getInt("detourMinutes"),
            reason = item.optString("reason"),
            tags = buildList {
                for (tagIndex in 0 until tags.length()) {
                    add(tags.getString(tagIndex))
                }
            },
            address = item.optString("address"),
            imageUrl = item.optString("imageUrl"),
            latitude = item.optDouble("latitude").takeUnless { it.isNaN() },
            longitude = item.optDouble("longitude").takeUnless { it.isNaN() },
            isOpen = item.optBoolean("isOpen", true),
        ),
        savedAtMillis = item.getLong("savedAtMillis"),
    )
}

private const val LEGACY_SAVED_STORAGE = "saved_places"
private const val SAVED_PLACES_KEY = "entries"
private const val SAVED_PLACES_MIGRATION_PREFERENCES = "saved_places_room_migration"
private const val SAVED_PLACES_MIGRATION_COMPLETE_KEY = "complete"
