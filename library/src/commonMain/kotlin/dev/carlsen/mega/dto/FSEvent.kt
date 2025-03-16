package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * FSEvent - event for various file system events
 *
 * Delete (a=d)
 * Update attr (a=u)
 * New nodes (a=t)
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class FSEvent(
    @SerialName("a") val cmd: String,

    @SerialName("t") val t: FSEventT? = null,
    @SerialName("ou") val owner: String? = null,

    @SerialName("n") val n: String? = null,
    @SerialName("u") val user: String? = null,
    @SerialName("at") val attr: String? = null,
    @SerialName("k") val key: String? = null,
    @SerialName("ts") val ts: Long? = null,
    @SerialName("i") val i: String? = null,
) {
    @Serializable
    @JsonIgnoreUnknownKeys
    data class FSEventT(
        @SerialName("f") val files: List<FSNode>,
    )
}