package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * Events is received from a poll of the server to read the events
 *
 * Each event can be an error message or a different field so we delay
 * decoding
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class Events(
    @SerialName("w") val w: String? = null,
    @SerialName("sn") val sn: String? = null,
    @SerialName("a") val e: List<JsonElement> = listOf(),
)