package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class FSNode(
    @SerialName("h") val handle: String,
    @SerialName("p") val parent: String,
    @SerialName("u") val user: String,
    @SerialName("t") val type: Int,
    @SerialName("a") val attr: String,
    @SerialName("k") val key: String? = null,
    @SerialName("r") val shareAccessLevel: Int? = null,
    @SerialName("ts") val ts: Long,
    @SerialName("su") val sharingUser: String? = null,
    @SerialName("sk") val shareKey: String? = null,
    @SerialName("s") val fileSize: Long? = null,
)