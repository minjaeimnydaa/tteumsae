package com.tteumsae.app.ui.navigation

enum class AppDestination {
    HOME,
    SAVED,
    SETTINGS,
    PROFILE,
    LOCATION,
    LOADING,
    RESULTS,
    DETAIL,
}

enum class MainTab {
    EXPLORE,
    SAVED,
    SETTINGS,
}

fun previousDestination(current: AppDestination): AppDestination = when (current) {
    AppDestination.SAVED,
    AppDestination.SETTINGS,
    AppDestination.LOCATION,
    -> AppDestination.HOME

    AppDestination.PROFILE -> AppDestination.SETTINGS

    AppDestination.LOADING,
    AppDestination.RESULTS,
    -> AppDestination.LOCATION

    AppDestination.DETAIL -> AppDestination.RESULTS
    AppDestination.HOME -> AppDestination.HOME
}

fun safeRestoredDestination(
    current: AppDestination,
    hasLocations: Boolean,
    hasResults: Boolean,
    hasDetail: Boolean,
): AppDestination = when (current) {
    AppDestination.LOADING,
    -> AppDestination.LOCATION

    AppDestination.RESULTS -> when {
        hasResults -> AppDestination.RESULTS
        hasLocations -> AppDestination.LOCATION
        else -> AppDestination.LOCATION
    }

    AppDestination.DETAIL -> when {
        hasResults && hasDetail -> AppDestination.DETAIL
        hasResults -> AppDestination.RESULTS
        hasLocations -> AppDestination.LOCATION
        else -> AppDestination.LOCATION
    }

    else -> current
}
