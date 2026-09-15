package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.closedir
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.opendir
import platform.posix.readdir
import platform.posix.remove

/** Test support for inspecting the spill files created by `truncateHeadTail`. */

@OptIn(ExperimentalForeignApi::class)
internal fun readSpillFile(path: String): String = memScoped {
    val file = fopen(path, "r") ?: error("Cannot open spill file: $path")
    try {
        val builder = StringBuilder()
        val buffer = ByteArray(4096)
        while (true) {
            val read = fread(buffer.refTo(0), 1.convert(), buffer.size.convert(), file).toInt()
            if (read <= 0) break
            builder.append(buffer.decodeToString(0, read))
        }
        builder.toString()
    } finally {
        fclose(file)
    }
}

internal fun deleteSpillFile(path: String) {
    remove(path)
}

/** Lists all `/tmp/sara-exec-*.log` spill files currently on disk. */
@OptIn(ExperimentalForeignApi::class)
internal fun listSaraExecSpillFiles(): List<String> = memScoped {
    val dir = opendir("/tmp") ?: return@memScoped emptyList()
    try {
        val spillFiles = mutableListOf<String>()
        while (true) {
            val entry = readdir(dir) ?: break
            val name = entry.pointed.d_name.toKString()
            if (name.startsWith("sara-exec-") && name.endsWith(".log")) {
                spillFiles.add("/tmp/$name")
            }
        }
        spillFiles
    } finally {
        closedir(dir)
    }
}
