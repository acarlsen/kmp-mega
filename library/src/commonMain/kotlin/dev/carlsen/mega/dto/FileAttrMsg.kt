package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileAttrMsg(
    @SerialName("a") val cmd: String,
    @SerialName("attr") val attr: String,
    @SerialName("key") val key: String,
    @SerialName("n") val n: String,
    @SerialName("i") val i: String,
)