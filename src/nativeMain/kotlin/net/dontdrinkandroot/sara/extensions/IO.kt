package net.dontdrinkandroot.sara.extensions

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite

/**
 * Writes [content] to [path] (overwriting any existing file). Uses the POSIX `fopen`/`fwrite`
 * API directly because kotlinx-io's [kotlinx.io.files.SystemFileSystem] lacks an atomic
 * whole-file writer in the current Native target.
 */
@OptIn(ExperimentalForeignApi::class)
fun writeWholeFile(path: String, content: String) {
    val file = fopen(path, "w") ?: throw RuntimeException("Unable to open file for writing: $path")
    try {
        val bytes = content.encodeToByteArray()
        bytes.usePinned { pinned ->
            fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
        }
        if (fflush(file) != 0) throw RuntimeException("Failed to flush file buffer for $path")
    } finally {
        fclose(file)
    }
}
