@file:OptIn(ExperimentalSerializationApi::class)

package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class FilesResp(
    @SerialName("f") val f: List<FSNode>,

    @SerialName("ok") val ok: List<OkItem>,

    @SerialName("s") val s: List<SItem>,
    @SerialName("u") val user: List<UserItem>,
    @SerialName("sn") val sn: String,
) {
    @Serializable
    @JsonIgnoreUnknownKeys
    data class OkItem(
        @SerialName("h") val handle: String,
        @SerialName("k") val key: String,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    data class SItem(
        @SerialName("h") val hash: String,
        @SerialName("u") val user: String,
    )

    @Serializable
    @JsonIgnoreUnknownKeys
    data class UserItem(
        @SerialName("u") val user: String,
        @SerialName("c") val c: Int,
        @SerialName("m") val email: String,
    )
}