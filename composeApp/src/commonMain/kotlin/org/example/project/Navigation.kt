package org.example.project

sealed class Screen {
    object Home : Screen()
    data class FolderView(val folderId: String, val folderName: String, val parentId: String?) : Screen()
    data class Editor(val noteId: String?, val folderId: String?, val folderName: String?) : Screen()
}
