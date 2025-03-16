package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileDeleteMsg(
    @SerialName("a") val cmd: String,
    @SerialName("n") val n: String,
    @SerialName("i") val i: String,
)