package org.example.project

/**
 * 파일 포맷:
 *   line 0: 제목
 *   line 1: markdown:true 또는 markdown:false
 *   line 2: (빈 줄)
 *   line 3~: 본문
 *
 * 일반 첨부 파일 목록: <noteId>.attachments (줄당 절대 경로)
 */
class MemoRepository {
    private val notesDir   get() = FileSystem.join(FileSystem.baseDir, "notes")
    private val foldersDir get() = FileSystem.join(FileSystem.baseDir, "folders")

    init {
        FileSystem.createDirs(notesDir)
        FileSystem.createDirs(foldersDir)
    }

    // ── 폴더 ──────────────────────────────────────────────────────────────────

    fun listFolders(parentId: String?): List<MemoFolder> {
        val dir = if (parentId == null) foldersDir else FileSystem.join(foldersDir, parentId, "sub")
        if (!FileSystem.exists(dir)) return emptyList()
        return FileSystem.listPaths(dir).filter { FileSystem.isDir(it) }.mapNotNull { path ->
            val id = FileSystem.fileName(path)
            val name = FileSystem.readText(FileSystem.join(path, ".name")) ?: return@mapNotNull null
            val cover = FileSystem.readText(FileSystem.join(path, ".cover"))?.trim()?.takeIf { FileSystem.exists(it) }
            MemoFolder(id, name.trim(), parentId, cover)
        }
    }

    fun createFolder(name: String, parentId: String?): MemoFolder {
        val id = newId()
        val dir = folderDir(id, parentId)
        FileSystem.createDirs(dir)
        FileSystem.createDirs(FileSystem.join(dir, "notes"))
        FileSystem.writeText(FileSystem.join(dir, ".name"), name)
        return MemoFolder(id, name, parentId)
    }

    fun renameFolder(folderId: String, parentId: String?, newName: String) {
        FileSystem.writeText(FileSystem.join(folderDir(folderId, parentId), ".name"), newName)
    }

    fun deleteFolder(folderId: String, parentId: String?) = FileSystem.delete(folderDir(folderId, parentId))

    fun getFolderCover(folderId: String, parentId: String?): String? {
        val path = FileSystem.join(folderDir(folderId, parentId), ".cover")
        return FileSystem.readText(path)?.trim()?.takeIf { FileSystem.exists(it) }
    }

    fun setFolderCover(folderId: String, parentId: String?, imageSrcPath: String) {
        val dir = folderDir(folderId, parentId)
        val ext = imageSrcPath.substringAfterLast(".", "jpg")
        val dest = FileSystem.join(dir, "cover.$ext")
        FileSystem.copyFile(imageSrcPath, dest)
        FileSystem.writeText(FileSystem.join(dir, ".cover"), dest)
    }

    // ── 메모 ──────────────────────────────────────────────────────────────────

    fun listNotes(folderId: String?): List<MemoNote> {
        val dir = notesDirFor(folderId)
        if (!FileSystem.exists(dir)) return emptyList()
        return FileSystem.listPaths(dir).filter { it.endsWith(".md") }.map { path ->
            parseNote(FileSystem.fileName(path).removeSuffix(".md"), path, folderId)
        }.sortedByDescending { it.modifiedAt }
    }

    fun loadNote(id: String, folderId: String?): MemoNote =
        parseNote(id, notePath(id, folderId), folderId)

    fun saveNote(note: MemoNote) {
        FileSystem.createDirs(notesDirFor(note.folderId))
        FileSystem.writeText(
            notePath(note.id, note.folderId),
            "${note.title}\nmarkdown:${note.useMarkdown}\n\n${note.content}"
        )
    }

    fun deleteNote(id: String, folderId: String?) = FileSystem.delete(notePath(id, folderId))

    // ── 일반 모드 첨부 ────────────────────────────────────────────────────────

    fun getPlainAttachments(noteId: String, folderId: String?): List<String> {
        val metaPath = attachmentsMetaPath(noteId, folderId)
        return FileSystem.readText(metaPath)?.lines()?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun addPlainAttachment(noteId: String, folderId: String?, srcPath: String): String {
        val attachDir = FileSystem.join(notesDirFor(folderId), "${noteId}_att")
        FileSystem.createDirs(attachDir)
        val name = FileSystem.fileName(srcPath)
        val dest = FileSystem.join(attachDir, name)
        FileSystem.copyFile(srcPath, dest)
        val existing = getPlainAttachments(noteId, folderId)
        FileSystem.writeText(attachmentsMetaPath(noteId, folderId), (existing + dest).joinToString("\n"))
        return dest
    }

    fun removePlainAttachment(noteId: String, folderId: String?, path: String) {
        val updated = getPlainAttachments(noteId, folderId).filter { it != path }
        FileSystem.writeText(attachmentsMetaPath(noteId, folderId), updated.joinToString("\n"))
    }

    // ── 공통 첨부 (파일 복사 후 절대 경로 반환) ──────────────────────────────

    fun addAttachment(noteId: String, folderId: String?, srcPath: String): String {
        val attachDir = FileSystem.join(notesDirFor(folderId), "${noteId}_att")
        FileSystem.createDirs(attachDir)
        val name = FileSystem.fileName(srcPath)
        val dest = FileSystem.join(attachDir, name)
        FileSystem.copyFile(srcPath, dest)
        return dest
    }

    // ── 마크다운 모드 첨부 ────────────────────────────────────────────────────

    fun attachFileMarkdown(noteId: String, folderId: String?, srcPath: String): String {
        val attachDir = FileSystem.join(notesDirFor(folderId), "${noteId}_att")
        FileSystem.createDirs(attachDir)
        val name = FileSystem.fileName(srcPath)
        val dest = FileSystem.join(attachDir, name)
        FileSystem.copyFile(srcPath, dest)
        val relPath = "${noteId}_att/$name"
        val ext = name.substringAfterLast(".", "").lowercase()
        return when {
            ext in setOf("png","jpg","jpeg","gif","webp","bmp","svg") -> "\n![${name.substringBeforeLast(".")}]($relPath)\n"
            ext in setOf("mp3","wav","m4a","ogg","aac","flac")        -> "\n[🎵 $name]($relPath)\n"
            else                                                       -> "\n[📎 $name]($relPath)\n"
        }
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────

    private fun folderDir(folderId: String, parentId: String?): String =
        if (parentId == null) FileSystem.join(foldersDir, folderId)
        else FileSystem.join(foldersDir, parentId, "sub", folderId)

    private fun notesDirFor(folderId: String?): String =
        if (folderId == null) notesDir
        else FileSystem.join(foldersDir, folderId, "notes")

    private fun notePath(id: String, folderId: String?) =
        FileSystem.join(notesDirFor(folderId), "$id.md")

    private fun attachmentsMetaPath(noteId: String, folderId: String?) =
        FileSystem.join(notesDirFor(folderId), "$noteId.attachments")

    private fun parseNote(id: String, path: String, folderId: String?): MemoNote {
        val raw = FileSystem.readText(path) ?: ""
        val lines = raw.lines()
        val title = lines.getOrNull(0)?.trim()?.ifBlank { "제목 없음" } ?: "제목 없음"
        val useMarkdown: Boolean
        val contentStart: Int
        if (lines.size > 1 && lines[1].startsWith("markdown:")) {
            useMarkdown = lines[1].removePrefix("markdown:").trim() == "true"
            contentStart = 3
        } else {
            useMarkdown = false
            contentStart = 2
        }
        val content = if (lines.size > contentStart) lines.drop(contentStart).joinToString("\n") else ""
        return MemoNote(id, title, content, folderId, 0L, useMarkdown)
    }
}
