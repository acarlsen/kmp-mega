import androidx.compose.ui.window.Window
import dev.carlsen.mega.sample.Application
import platform.AppKit.NSApp
import platform.AppKit.NSApplication

fun main() {
    NSApplication.sharedApplication()
    Window("kmp-mega") {
        Application()
    }
    NSApp?.run()
}

