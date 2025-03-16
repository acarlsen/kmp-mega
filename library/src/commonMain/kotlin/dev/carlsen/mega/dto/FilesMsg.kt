package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FilesMsg(
    @SerialName("a") val cmd: String,
    @SerialName("c") val c: Int,
)