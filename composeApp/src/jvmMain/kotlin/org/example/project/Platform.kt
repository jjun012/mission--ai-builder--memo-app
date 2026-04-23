package org.example.project

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

actual object FileSystem {
    actual val baseDir: String = File(
        System.getProperty("user.home"), "Documents/MemoApp"
    ).also { it.mkdirs() }.absolutePath

    actual fun readText(path: String)                     = runCatching { File(path).readText() }.getOrNull()
    actual fun writeText(path: String, content: String)   { File(path).also { it.parentFile?.mkdirs() }.writeText(content) }
    actual fun listPaths(dirPath: String)                 = File(dirPath).listFiles()?.map { it.absolutePath } ?: emptyList()
    actual fun createDirs(path: String)                   = File(path).mkdirs()
    actual fun exists(path: String)                       = File(path).exists()
    actual fun delete(path: String)                       = File(path).deleteRecursively()
    actual fun isDir(path: String)                        = File(path).isDirectory
    actual fun fileName(path: String)                     = File(path).name
    actual fun join(vararg parts: String)                 = parts.joinToString(File.separator)
    actual fun copyFile(src: String, dest: String): Boolean = runCatching {
        File(src).copyTo(File(dest).also { it.parentFile?.mkdirs() }, overwrite = true)
    }.isSuccess
}

actual fun newId()             = UUID.randomUUID().toString().substring(0, 8)
actual fun currentTimeMillis() = System.currentTimeMillis()

actual fun loadImageBitmap(path: String): ImageBitmap? =
    runCatching { ImageIO.read(File(path))?.toComposeImageBitmap() }.getOrNull()

actual fun pickFile(title: String, vararg extensions: String): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        isAcceptAllFileFilterUsed = extensions.isEmpty()
        if (extensions.isNotEmpty()) {
            fileFilter = FileNameExtensionFilter(title, *extensions)
            isAcceptAllFileFilterUsed = true
        }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFile.absolutePath else null
}

actual fun pickFiles(title: String): List<String> {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        isMultiSelectionEnabled = true
        isAcceptAllFileFilterUsed = true
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
        chooser.selectedFiles.map { it.absolutePath } else emptyList()
}
