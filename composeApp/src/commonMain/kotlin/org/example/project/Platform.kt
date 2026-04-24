package org.example.project

import androidx.compose.ui.graphics.ImageBitmap

expect object FileSystem {
    val baseDir: String
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun listPaths(dirPath: String): List<String>
    fun createDirs(path: String): Boolean
    fun exists(path: String): Boolean
    fun delete(path: String): Boolean
    fun isDir(path: String): Boolean
    fun fileName(path: String): String
    fun join(vararg parts: String): String
    fun copyFile(src: String, dest: String): Boolean
    fun fileModifiedAt(path: String): Long
}

expect fun newId(): String
expect fun currentTimeMillis(): Long
expect fun localTimeString(): String   // "HH:mm" 로컬 시각
expect fun loadImageBitmap(path: String): ImageBitmap?
expect fun pickFile(title: String, vararg extensions: String): String?
expect fun pickFiles(title: String): List<String>
