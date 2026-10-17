package com.tteumsae.app.domain.account

enum class AgeGroup {
    UNDER_20,
    TWENTIES,
    THIRTIES,
    FORTIES,
    FIFTIES,
    SIXTY_PLUS,
    PREFER_NOT_TO_SAY,
}

enum class Gender {
    FEMALE,
    MALE,
    OTHER,
    PREFER_NOT_TO_SAY,
}

data class UserProfile(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val ageGroup: AgeGroup?,
    val gender: Gender?,
)
