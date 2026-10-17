package com.tteumsae.app

import android.content.Context
import androidx.room.Room
import com.tteumsae.app.data.account.AccountDeletionApi
import com.tteumsae.app.data.TteumsaeApi
import com.tteumsae.app.data.route.RouteGateway
import com.tteumsae.app.data.auth.AuthRepository
import com.tteumsae.app.data.auth.DisabledAuthGateway
import com.tteumsae.app.data.auth.SupabaseAuthGateway
import com.tteumsae.app.data.auth.SupabaseClientProvider
import com.tteumsae.app.data.local.RoomDatabaseTransactionRunner
import com.tteumsae.app.data.local.SavedPlacePreferencesMigration
import com.tteumsae.app.data.local.SharedPreferencesSavedPlaceMigrationPreferences
import com.tteumsae.app.data.local.TteumsaeDatabase
import com.tteumsae.app.data.profile.ProfileRepository
import com.tteumsae.app.data.profile.SupabaseOAuthProfileMetadataSource
import com.tteumsae.app.data.profile.SupabaseProfileRemoteDataSource
import com.tteumsae.app.data.saved.RoomSavedPlacesRepository
import com.tteumsae.app.data.saved.SavedPlacesRepository
import com.tteumsae.app.reminder.ActiveTripStore
import com.tteumsae.app.reminder.AlarmManagerDepartureReminderScheduler
import com.tteumsae.app.reminder.DepartureReminderScheduler
import com.tteumsae.app.reminder.DepartureReminderCoordinator
import com.tteumsae.app.reminder.SharedPreferencesActiveTripPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: TteumsaeDatabase = Room.databaseBuilder(
        context.applicationContext,
        TteumsaeDatabase::class.java,
        TteumsaeDatabase.NAME,
    ).build()

    private val transactionRunner = RoomDatabaseTransactionRunner(database)

    val savedPlacesRepository: SavedPlacesRepository = RoomSavedPlacesRepository(
        dao = database.savedPlaceDao(),
        transactionRunner = transactionRunner,
    )

    val supabaseClient = SupabaseClientProvider.createOrNull()

    val authRepository = AuthRepository(
        gateway = supabaseClient?.let(::SupabaseAuthGateway) ?: DisabledAuthGateway(),
        scope = applicationScope,
    )

    val profileRepository: ProfileRepository? = supabaseClient?.let { client ->
        ProfileRepository(
            remote = SupabaseProfileRemoteDataSource(client),
            metadataSource = SupabaseOAuthProfileMetadataSource(client),
        )
    }

    val accountDeletionClient = AccountDeletionApi()

    val routeGateway: RouteGateway = TteumsaeApi()

    val activeTripStore = ActiveTripStore(
        SharedPreferencesActiveTripPreferences(context.applicationContext),
    )

    val departureReminderScheduler: DepartureReminderScheduler =
        AlarmManagerDepartureReminderScheduler(context.applicationContext)

    val departureReminderCoordinator = DepartureReminderCoordinator(
        store = activeTripStore,
        scheduler = departureReminderScheduler,
    )

    val savedPlacePreferencesMigration = SavedPlacePreferencesMigration(
        preferences = SharedPreferencesSavedPlaceMigrationPreferences(context.applicationContext),
        dao = database.savedPlaceDao(),
        transactionRunner = transactionRunner,
    )
}
