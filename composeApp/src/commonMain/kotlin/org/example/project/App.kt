package org.example.project

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── 색상 스킴 (파란 계열, 깔끔) ─────────────────────────────────────────────

private val Blue600  = Color(0xFF2563EB)
private val Blue400  = Color(0xFF60A5FA)
private val Blue100  = Color(0xFFDBEAFE)
private val Blue900  = Color(0xFF1E3A8A)
private val Gray50   = Color(0xFFF9FAFB)
private val Gray100  = Color(0xFFF3F4F6)
private val Gray200  = Color(0xFFE5E7EB)
private val Gray500  = Color(0xFF6B7280)
private val Gray900  = Color(0xFF111827)
private val Dark900  = Color(0xFF0F172A)
private val Dark800  = Color(0xFF1E293B)
private val Dark700  = Color(0xFF334155)
private val Dark400  = Color(0xFF94A3B8)
private val Red500   = Color(0xFFEF4444)

private val LightColors = lightColorScheme(
    primary              = Blue600,
    onPrimary            = Color.White,
    primaryContainer     = Blue100,
    onPrimaryContainer   = Blue900,
    secondary            = Gray500,
    onSecondary          = Color.White,
    secondaryContainer   = Gray100,
    onSecondaryContainer = Gray900,
    background           = Color.White,
    onBackground         = Gray900,
    surface              = Color.White,
    onSurface            = Gray900,
    surfaceVariant       = Gray100,
    onSurfaceVariant     = Gray500,
    outline              = Gray200,
    outlineVariant       = Gray200,
    error                = Red500,
    onError              = Color.White,
)

private val DarkColors = darkColorScheme(
    primary              = Blue400,
    onPrimary            = Color(0xFF1E3A5F),
    primaryContainer     = Color(0xFF1D4ED8),
    onPrimaryContainer   = Blue100,
    secondary            = Dark400,
    onSecondary          = Dark800,
    secondaryContainer   = Dark700,
    onSecondaryContainer = Color(0xFFCBD5E1),
    background           = Dark900,
    onBackground         = Color(0xFFF1F5F9),
    surface              = Dark800,
    onSurface            = Color(0xFFF1F5F9),
    surfaceVariant       = Dark700,
    onSurfaceVariant     = Dark400,
    outline              = Dark700,
    outlineVariant       = Dark700,
    error                = Color(0xFFF87171),
    onError              = Color(0xFF7F1D1D),
)

@Composable
fun App() {
    var isDark by remember { mutableStateOf(false) }
    MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors) {
        MemoNavHost(isDark = isDark, onThemeToggle = { isDark = !isDark })
    }
}

@Composable
fun MemoNavHost(isDark: Boolean, onThemeToggle: () -> Unit) {
    val repo = remember { MemoRepository() }
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }

    fun push(s: Screen) { stack = stack + s }
    fun pop()           { if (stack.size > 1) stack = stack.dropLast(1) }

    when (val screen = stack.last()) {
        is Screen.Home -> HomeScreen(
            repo = repo, isDark = isDark, onThemeToggle = onThemeToggle,
            onOpenFolder = { push(Screen.FolderView(it.id, it.name, it.parentId)) },
            onOpenNote   = { push(Screen.Editor(it.id, it.folderId, null)) },
            onNewNote    = { push(Screen.Editor(null, null, null)) }
        )
        is Screen.FolderView -> HomeScreen(
            repo = repo, isDark = isDark, onThemeToggle = onThemeToggle,
            folderId = screen.folderId, folderName = screen.folderName, parentId = screen.parentId,
            onBack       = ::pop,
            onOpenFolder = { push(Screen.FolderView(it.id, it.name, screen.folderId)) },
            onOpenNote   = { push(Screen.Editor(it.id, it.folderId, screen.folderName)) },
            onNewNote    = { push(Screen.Editor(null, screen.folderId, screen.folderName)) }
        )
        is Screen.Editor -> EditorScreen(
            repo = repo, noteId = screen.noteId,
            folderId = screen.folderId, folderName = screen.folderName,
            onBack = ::pop
        )
    }
}
