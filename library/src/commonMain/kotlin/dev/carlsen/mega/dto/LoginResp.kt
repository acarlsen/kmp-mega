package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class LoginResp(
    @SerialName("csid") val csid: String,
    @SerialName("privk") val privk: String,
    @SerialName("k") val key: String,
    @SerialName("ach") val ach: Int,
    @SerialName("sek") val sessionKey: String,
    @SerialName("u") val u: String,
)