package dev.carlsen.mega.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class QuotaResp(
    // Mstrg is total capacity in bytes
    @SerialName("mstrg") val spaceCapacityBytes: ULong,
    // Cstrg is used capacity in bytes
    @SerialName("cstrg") val spaceUsedBytes: ULong,
    // Per folder usage in bytes?
    @SerialName("cstrgn") val cstrgn: Map<String, List<Long>>,
)