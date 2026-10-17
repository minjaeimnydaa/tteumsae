package com.tteumsae.app.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("age_group") val ageGroup: String?,
    val gender: String?,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ProfileInsertDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("age_group") val ageGroup: String?,
    val gender: String?,
)

@Serializable
data class ProfileUpdateDto(
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("age_group") val ageGroup: String?,
    val gender: String?,
)
