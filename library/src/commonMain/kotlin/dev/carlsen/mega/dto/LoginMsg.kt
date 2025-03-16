package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginMsg(
    @SerialName("a") val cmd: String,
    @SerialName("user") val user: String,
    @SerialName("uh") val handle: String,
    @SerialName("sek") val sessionKey: String? = null,
    @SerialName("si") val si: String? = null,
    @SerialName("mfa") val mfa: String? = null,
)