package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.refTo
import kotlinx.cinterop.toKString
import platform.posix.fgets
import platform.posix.pclose
import platform.posix.popen

/** Output and terminal status of a shell command execution. */
data class CommandExecution(val output: String, val exitStatus: ExitStatus)

/**
 * Executes a system command (stderr merged into stdout) and returns its output together
 * with the decoded `pclose` wait status.
 */
@OptIn(ExperimentalForeignApi::class)
fun executeCommandWithStatus(command: String): CommandExecution = memScoped {
    val file = popen("$command 2>&1", "r") ?: throw RuntimeException("Failed to execute command")

    val output = StringBuilder()
    val buffer = ByteArray(4096)
    var waitStatus = -1

    try {
        while (true) {
            val line = fgets(buffer.refTo(0), buffer.size, file) ?: break
            output.append(line.toKString())
        }
    } finally {
        waitStatus = pclose(file)
    }

    CommandExecution(output.toString(), decodeWaitStatus(waitStatus))
}

/**
 * Executes a system command and returns its output.
 */
fun executeCommand(command: String): String = executeCommandWithStatus(command).output


fun executeCommandSafe(command: String): String? = try {
    executeCommand(command).trim()
} catch (e: Exception) {
    null
}
