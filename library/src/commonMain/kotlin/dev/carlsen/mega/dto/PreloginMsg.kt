package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreloginMsg(
    @SerialName("a") val cmd: String,
    @SerialName("user") val user: String,
)