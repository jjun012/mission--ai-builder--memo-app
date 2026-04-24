package org.example.project

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() {
    System.setProperty("apple.awt.application.appearance", "system")
    System.setProperty("compose.swing.render.on.graphics", "true")
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "메모장",
            state = rememberWindowState(width = 1100.dp, height = 750.dp)
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = java.awt.Dimension(700, 500)
            }
            App()
        }
    }
}
