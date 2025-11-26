package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

/**
 * Executes a system command and returns its output.
 */
@OptIn(ExperimentalForeignApi::class)
fun executeCommand(command: String): String = memScoped {
    val file = popen("$command 2>&1", "r") ?: throw RuntimeException("Failed to execute command")

    val output = StringBuilder()
    val buffer = ByteArray(4096)

    try {
        while (true) {
            val line = fgets(buffer.refTo(0), buffer.size, file) ?: break
            output.append(line.toKString())
        }
    } finally {
        pclose(file)
    }

    output.toString()
}


fun executeCommandSafe(command: String): String? = try {
    executeCommand(command).trim()
} catch (e: Exception) {
    null
}
