package com.tteumsae.app.data.profile

import com.tteumsae.app.domain.account.AccountSession
import com.tteumsae.app.domain.account.AgeGroup
import com.tteumsae.app.domain.account.Gender
import com.tteumsae.app.domain.account.LoginProvider
import com.tteumsae.app.domain.account.UserProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryTest {
    @Test
    fun missing_profile_is_created_from_provider_nickname_and_avatar_without_email() = runBlocking {
        val remote = FakeProfileRemoteDataSource()
        val metadata = FakeMetadataSource(
            OAuthProfileMetadata(
                displayName = "카카오 닉네임",
                avatarUrl = "https://example.com/avatar.png",
            ),
        )
        val repository = ProfileRepository(remote, metadata)

        val profile = repository.loadOrCreate(signedIn())

        assertEquals("카카오 닉네임", profile.displayName)
        assertEquals("https://example.com/avatar.png", profile.avatarUrl)
        assertNull(profile.ageGroup)
        assertNull(profile.gender)
        assertEquals("user-123", remote.inserted?.userId)
        assertFalse(ProfileInsertDto::class.java.declaredFields.any { it.name.contains("email", true) })
    }

    @Test
    fun existing_profile_is_loaded_without_overwriting_it_from_provider_metadata() = runBlocking {
        val remote = FakeProfileRemoteDataSource(
            stored = dto(displayName = "직접 정한 닉네임"),
        )
        val metadata = FakeMetadataSource(OAuthProfileMetadata("새 소셜 이름", null))
        val repository = ProfileRepository(remote, metadata)

        val profile = repository.loadOrCreate(signedIn())

        assertEquals("직접 정한 닉네임", profile.displayName)
        assertEquals(0, metadata.calls)
        assertNull(remote.inserted)
    }

    @Test
    fun concurrent_first_login_conflict_reselects_the_owner_row() = runBlocking {
        val remote = FakeProfileRemoteDataSource(conflictOnInsert = true)
        val repository = ProfileRepository(
            remote,
            FakeMetadataSource(OAuthProfileMetadata("동시 로그인", null)),
        )

        val profile = repository.loadOrCreate(signedIn())

        assertEquals("동시에 생성된 프로필", profile.displayName)
        assertEquals(2, remote.findCalls)
    }

    @Test
    fun blank_nickname_is_normalized_to_null_and_updates_only_editable_columns() = runBlocking {
        val remote = FakeProfileRemoteDataSource(stored = dto())
        val repository = ProfileRepository(remote, FakeMetadataSource(OAuthProfileMetadata(null, null)))

        repository.update(
            UserProfile(
                userId = "user-123",
                displayName = "   ",
                avatarUrl = null,
                ageGroup = null,
                gender = null,
            ),
        )

        assertNull(remote.updated?.displayName)
        assertFalse(ProfileUpdateDto::class.java.declaredFields.any { it.name == "userId" })
        assertFalse(ProfileUpdateDto::class.java.declaredFields.any { it.name.contains("created", true) })
        assertFalse(ProfileUpdateDto::class.java.declaredFields.any { it.name.contains("updated", true) })
    }

    @Test
    fun nickname_over_40_characters_is_rejected_before_a_remote_update() {
        val remote = FakeProfileRemoteDataSource(stored = dto())
        val repository = ProfileRepository(remote, FakeMetadataSource(OAuthProfileMetadata(null, null)))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.update(
                    UserProfile(
                        userId = "user-123",
                        displayName = "가".repeat(41),
                        avatarUrl = null,
                        ageGroup = null,
                        gender = null,
                    ),
                )
            }
        }
        assertNull(remote.updated)
    }

    @Test
    fun optional_demographics_and_explicit_prefer_not_to_say_round_trip() = runBlocking {
        val remote = FakeProfileRemoteDataSource(stored = dto())
        val repository = ProfileRepository(remote, FakeMetadataSource(OAuthProfileMetadata(null, null)))
        val input = UserProfile(
            userId = "user-123",
            displayName = "신홍",
            avatarUrl = null,
            ageGroup = AgeGroup.PREFER_NOT_TO_SAY,
            gender = Gender.PREFER_NOT_TO_SAY,
        )

        val updated = repository.update(input)

        assertEquals(AgeGroup.PREFER_NOT_TO_SAY, updated.ageGroup)
        assertEquals(Gender.PREFER_NOT_TO_SAY, updated.gender)
        assertEquals("PREFER_NOT_TO_SAY", remote.updated?.ageGroup)
        assertEquals("PREFER_NOT_TO_SAY", remote.updated?.gender)
    }

    @Test
    fun oauth_metadata_mapper_supports_kakao_and_google_field_names() {
        val kakao = OAuthProfileMetadataMapper.from(
            mapOf("nickname" to "카카오", "avatar_url" to "https://kakao/avatar"),
        )
        val google = OAuthProfileMetadataMapper.from(
            mapOf("full_name" to "Google User", "picture" to "https://google/avatar"),
        )

        assertEquals("카카오", kakao.displayName)
        assertEquals("https://kakao/avatar", kakao.avatarUrl)
        assertEquals("Google User", google.displayName)
        assertEquals("https://google/avatar", google.avatarUrl)
        assertTrue(OAuthProfileMetadata::class.java.declaredFields.none { it.name.contains("email", true) })
    }

    private fun signedIn() = AccountSession.SignedIn("user-123", LoginProvider.KAKAO)

    private fun dto(
        displayName: String? = null,
        ageGroup: String? = null,
        gender: String? = null,
    ) = ProfileDto(
        userId = "user-123",
        displayName = displayName,
        avatarUrl = null,
        ageGroup = ageGroup,
        gender = gender,
    )
}

private class FakeMetadataSource(
    private val metadata: OAuthProfileMetadata,
) : OAuthProfileMetadataSource {
    var calls = 0

    override fun current(userId: String): OAuthProfileMetadata {
        calls += 1
        return metadata
    }
}

private class FakeProfileRemoteDataSource(
    var stored: ProfileDto? = null,
    private val conflictOnInsert: Boolean = false,
) : ProfileRemoteDataSource {
    var findCalls = 0
    var inserted: ProfileInsertDto? = null
    var updated: ProfileUpdateDto? = null

    override suspend fun find(userId: String): ProfileDto? {
        findCalls += 1
        return stored
    }

    override suspend fun insert(profile: ProfileInsertDto): ProfileDto {
        inserted = profile
        if (conflictOnInsert) {
            stored = ProfileDto(
                userId = profile.userId,
                displayName = "동시에 생성된 프로필",
                avatarUrl = null,
                ageGroup = null,
                gender = null,
            )
            throw ProfileAlreadyExistsException()
        }
        return ProfileDto(
            userId = profile.userId,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            ageGroup = profile.ageGroup,
            gender = profile.gender,
        ).also { stored = it }
    }

    override suspend fun update(userId: String, profile: ProfileUpdateDto): ProfileDto {
        updated = profile
        return ProfileDto(
            userId = userId,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            ageGroup = profile.ageGroup,
            gender = profile.gender,
        ).also { stored = it }
    }
}
