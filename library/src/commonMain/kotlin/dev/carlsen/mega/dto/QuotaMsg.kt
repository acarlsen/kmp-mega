package dev.carlsen.mega.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QuotaMsg(
    // Action, should be "uq" for quota request
    @SerialName("a") val cmd: String,
    // xfer should be 1
    @SerialName("xfer") val xfer: Int,
    // Without strg=1 only reports total capacity for account
    @SerialName("strg") val strg: Int? = null,
)