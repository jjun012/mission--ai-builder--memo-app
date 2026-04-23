package org.example.project

data class MemoFolder(
    val id: String,
    val name: String,
    val parentId: String?,
    val coverPath: String? = null
)

data class MemoNote(
    val id: String,
    val title: String,
    val content: String,
    val folderId: String?,
    val modifiedAt: Long,
    val useMarkdown: Boolean = false
)
