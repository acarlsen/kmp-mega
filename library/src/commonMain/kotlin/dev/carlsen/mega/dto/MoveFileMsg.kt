package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoveFileMsg(
    @SerialName("a") val cmd: String,
    @SerialName("n") val n: String,
    @SerialName("t") val t: String,
    @SerialName("i") val i: String,
)