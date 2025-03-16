package dev.carlsen.mega

import dev.carlsen.mega.model.MegaException

enum class MegaError(val code: Int, val message: String) {
    // General errors
    EINTERNAL(-1, "Internal error occurred"),
    EARGS(-2, "Invalid arguments"),
    EAGAIN(-3, "Try again"),
    ERATELIMIT(-4, "Rate limit reached"),
    EBADRESP(Int.MIN_VALUE, "Bad response from server"),

    // Upload errors
    EFAILED(-5, "The upload failed. Please restart it from scratch"),
    ETOOMANY(-6, "Too many concurrent IP addresses are accessing this upload target URL"),
    ERANGE(-7, "The upload file packet is out of range or not starting and ending on a chunk boundary"),
    EEXPIRED(-8, "The upload target URL you are trying to access has expired. Please request a fresh one"),

    // Filesystem/Account errors
    ENOENT(-9, "Object (typically, node or user) not found"),
    ECIRCULAR(-10, "Circular linkage attempted"),
    EACCESS(-11, "Access violation"),
    EEXIST(-12, "Trying to create an object that already exists"),
    EINCOMPLETE(-13, "Trying to access an incomplete resource"),
    EKEY(-14, "A decryption operation failed"),
    ESID(-15, "Invalid or expired user session, please relogin"),
    EBLOCKED(-16, "User blocked"),
    EOVERQUOTA(-17, "Request over quota"),
    ETEMPUNAVAIL(-18, "Resource temporarily not available, please try again later"),
    EMACMISMATCH(Int.MIN_VALUE, "MAC verification failed"),
    EBADATTR(Int.MIN_VALUE, "Bad node attribute"),
    ETOOMANYCONNECTIONS(-19, "Too many connections on this resource."),
    EWRITE(-20, "File could not be written to (or failed post-write integrity check)."),
    EREAD(-21, "File could not be read from (or changed unexpectedly during reading)."),
    EAPPKEY(-22, "Invalid or missing application key."),
    ESSL(-23, "SSL verification failed"),
    EGOINGOVERQUOTA(-24, "Not enough quota"),
    EMFAREQUIRED(-26, "Multi-factor authentication required"),

    // Config errors
    EWORKER_LIMIT_EXCEEDED(Int.MIN_VALUE, "Maximum worker limit exceeded");

    fun toException(): MegaException = MegaException(message = message, code = code)

    companion object {
        fun parseError(errno: Int): MegaException? {
            if (errno == 0) return null

            return entries.find { it.code == errno }?.toException()
                ?: MegaException("Unknown mega error code: $errno")
        }
    }
}