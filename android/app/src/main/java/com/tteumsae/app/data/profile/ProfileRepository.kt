package com.tteumsae.app.data.profile

import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.AgeGroup
import com.tteumsae.app.domain.account.Gender
import com.tteumsae.app.domain.account.UserProfile

class ProfileRepository(
    private val remote: ProfileRemoteDataSource,
    private val metadataSource: OAuthProfileMetadataSource,
) {
    suspend fun loadOrCreate(user: AccountSession.SignedIn): UserProfile {
        require(user.userId.isNotBlank()) { "userId must not be blank" }
        remote.find(user.userId)?.let { return it.toDomain() }

        val metadata = metadataSource.current(user.userId)
        val insert = ProfileInsertDto(
            userId = user.userId,
            displayName = metadata.displayName.normalizedDisplayName(),
            avatarUrl = metadata.avatarUrl.normalizedAvatarUrl(),
            ageGroup = null,
            gender = null,
        )
        return try {
            remote.insert(insert).toDomain()
        } catch (_: ProfileAlreadyExistsException) {
            checkNotNull(remote.find(user.userId)) {
                "Profile conflict occurred but the owner row was not visible"
            }.toDomain()
        }
    }

    suspend fun update(profile: UserProfile): UserProfile {
        require(profile.userId.isNotBlank()) { "userId must not be blank" }
        val update = ProfileUpdateDto(
            displayName = profile.displayName.normalizedDisplayName(),
            avatarUrl = profile.avatarUrl.normalizedAvatarUrl(),
            ageGroup = profile.ageGroup?.name,
            gender = profile.gender?.name,
        )
        return remote.update(profile.userId, update).toDomain()
    }
}

private fun ProfileDto.toDomain(): UserProfile = UserProfile(
    userId = userId,
    displayName = displayName.normalizedDisplayName(),
    avatarUrl = avatarUrl.normalizedAvatarUrl(),
    ageGroup = ageGroup?.let(AgeGroup::valueOf),
    gender = gender?.let(Gender::valueOf),
)

private fun String?.normalizedDisplayName(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    require(normalized.length <= 40) { "displayName must be 40 characters or fewer" }
    return normalized
}

private fun String?.normalizedAvatarUrl(): String? {
    val normalized = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    require(normalized.length <= 2_048) { "avatarUrl must be 2048 characters or fewer" }
    return normalized
}
