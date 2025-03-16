package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DownloadMsg(
    @SerialName("a") val cmd: String,
    @SerialName("g") val g: Int,
    @SerialName("p") val p: String? = null,
    @SerialName("n") val n: String? = null,
    @SerialName("ssl") val ssl: Int? = null,
)