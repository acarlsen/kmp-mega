package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadMsg(
    @SerialName("a") val cmd: String,
    @SerialName("s") val s: Long,
    @SerialName("ssl") val ssl: Int? = null,
)