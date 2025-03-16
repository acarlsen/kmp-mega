package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

/**
 * GenericEvent is a generic event for parsing the Cmd type before
 * decoding more specifically
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class GenericEvent(
    @SerialName("a") val cmd: String,
)