package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UploadCompleteMsg(
    @SerialName("a") val cmd: String,
    @SerialName("t") val t: String,
    @SerialName("n") val n: List<UploadNode>,
    @SerialName("i") val i: String? = null,
) {
    @Serializable
    data class UploadNode(
        @SerialName("h") val h: String,
        @SerialName("t") val t: Int,
        @SerialName("a") val a: String,
        @SerialName("k") val k: String,
    )
}