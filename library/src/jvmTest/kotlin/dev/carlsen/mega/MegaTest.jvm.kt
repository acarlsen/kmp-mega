package dev.carlsen.mega

actual val megaUserName: String
    get() = System.getenv("MEGA_USER")
actual val megaPassword: String
    get() = System.getenv("MEGA_PASSWD")