package com.tteumsae.app.data.profile

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

interface ProfileRemoteDataSource {
    suspend fun find(userId: String): ProfileDto?

    suspend fun insert(profile: ProfileInsertDto): ProfileDto

    suspend fun update(userId: String, profile: ProfileUpdateDto): ProfileDto
}

data class OAuthProfileMetadata(
    val displayName: String?,
    val avatarUrl: String?,
)

fun interface OAuthProfileMetadataSource {
    fun current(userId: String): OAuthProfileMetadata
}

object OAuthProfileMetadataMapper {
    fun from(values: Map<String, String?>): OAuthProfileMetadata = OAuthProfileMetadata(
        displayName = firstNonBlank(values, "nickname", "full_name", "name", "display_name"),
        avatarUrl = firstNonBlank(values, "avatar_url", "picture", "profile_image"),
    )

    private fun firstNonBlank(values: Map<String, String?>, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> values[key]?.trim()?.takeIf(String::isNotEmpty) }
}

class SupabaseOAuthProfileMetadataSource(
    private val client: SupabaseClient,
) : OAuthProfileMetadataSource {
    override fun current(userId: String): OAuthProfileMetadata {
        val user = client.auth.currentUserOrNull()
            ?.takeIf { it.id == userId }
            ?: return OAuthProfileMetadata(displayName = null, avatarUrl = null)
        val values = user.userMetadata.orEmpty().mapValues { (_, value) ->
            (value as? JsonPrimitive)?.contentOrNull
        }
        return OAuthProfileMetadataMapper.from(values)
    }
}

class SupabaseProfileRemoteDataSource(
    private val client: SupabaseClient,
) : ProfileRemoteDataSource {
    override suspend fun find(userId: String): ProfileDto? = client
        .from(PROFILES_TABLE)
        .select {
            filter { eq("user_id", userId) }
        }
        .decodeList<ProfileDto>()
        .singleOrNull()

    override suspend fun insert(profile: ProfileInsertDto): ProfileDto = try {
        client.from(PROFILES_TABLE).insert(profile) {
            select()
        }.decodeSingle<ProfileDto>()
    } catch (error: PostgrestRestException) {
        if (error.code == UNIQUE_VIOLATION) throw ProfileAlreadyExistsException()
        throw error
    }

    override suspend fun update(userId: String, profile: ProfileUpdateDto): ProfileDto = client
        .from(PROFILES_TABLE)
        .update(profile) {
            select()
            filter { eq("user_id", userId) }
        }
        .decodeSingle<ProfileDto>()

    private companion object {
        const val PROFILES_TABLE = "profiles"
        const val UNIQUE_VIOLATION = "23505"
    }
}

class ProfileAlreadyExistsException : RuntimeException()
