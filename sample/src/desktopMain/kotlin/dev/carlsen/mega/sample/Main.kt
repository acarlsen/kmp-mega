package dev.carlsen.mega.sample

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

public fun main() {
    application {
        Window(onCloseRequest = ::exitApplication) {
            Application()
        }
    }
}
