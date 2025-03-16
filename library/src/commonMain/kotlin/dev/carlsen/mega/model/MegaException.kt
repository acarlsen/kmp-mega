package dev.carlsen.mega.model

class MegaException(message: String? = null, cause: Throwable? = null, val code: Int? = null) :
    Exception(message, cause)