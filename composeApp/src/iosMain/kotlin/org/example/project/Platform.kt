package org.example.project

import androidx.compose.ui.graphics.ImageBitmap
import platform.Foundation.*

actual object FileSystem {
    actual val baseDir: String by lazy {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val doc = paths.firstOrNull() as? String ?: NSTemporaryDirectory()
        val dir = "$doc/MemoApp"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        dir
    }

    actual fun readText(path: String): String? =
        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) as? String

    actual fun writeText(path: String, content: String) {
        val parent = path.substringBeforeLast("/")
        NSFileManager.defaultManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    actual fun listPaths(dirPath: String): List<String> {
        val items = NSFileManager.defaultManager.contentsOfDirectoryAtPath(dirPath, error = null) ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return (items as List<String>).map { "$dirPath/$it" }
    }

    actual fun createDirs(path: String) =
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)

    actual fun exists(path: String) = NSFileManager.defaultManager.fileExistsAtPath(path)
    actual fun delete(path: String) = NSFileManager.defaultManager.removeItemAtPath(path, error = null)

    actual fun isDir(path: String): Boolean {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return false
        return attrs[NSFileType] as? String == NSFileTypeDirectory
    }

    actual fun fileName(path: String) = path.substringAfterLast("/")
    actual fun join(vararg parts: String) = parts.joinToString("/")

    actual fun copyFile(src: String, dest: String): Boolean {
        val parent = dest.substringBeforeLast("/")
        NSFileManager.defaultManager.createDirectoryAtPath(parent, withIntermediateDirectories = true, attributes = null, error = null)
        return NSFileManager.defaultManager.copyItemAtPath(src, toPath = dest, error = null)
    }
    actual fun fileModifiedAt(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null) ?: return 0L
        val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000).toLong()
    }
}

actual fun newId() = NSUUID.UUID().UUIDString.substring(0, 8)
actual fun currentTimeMillis() = (NSDate.date().timeIntervalSince1970 * 1000).toLong()
actual fun localTimeString(): String {
    val cal = NSCalendar.currentCalendar
    val now = NSDate.date()
    val h = cal.component(platform.Foundation.NSCalendarUnitHour, fromDate = now)
    val m = cal.component(platform.Foundation.NSCalendarUnitMinute, fromDate = now)
    return "%02d:%02d".format(h, m)
}
actual fun loadImageBitmap(path: String): ImageBitmap? = null
actual fun pickFile(title: String, vararg extensions: String): String? = null
actual fun pickFiles(title: String): List<String> = emptyList()
