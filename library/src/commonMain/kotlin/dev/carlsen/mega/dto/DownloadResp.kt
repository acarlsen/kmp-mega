package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class DownloadResp(
    @SerialName("g") val g: String,
    @SerialName("s") val size: ULong,
    @SerialName("at") val attr: String,
    @SerialName("e") val err: ErrorMsg? = null,
)
