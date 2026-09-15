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
 *
 * The `exec 2>&1;` prefix makes the shell redirect its own fd 2 into the popen pipe, so
 * stderr of every command in a compound list (`a; b`, `a && b`, `a | b`) is captured.
 * A trailing `2>&1` would not do: it binds only to the last simple command of the list.
 */
@OptIn(ExperimentalForeignApi::class)
fun executeCommandWithStatus(command: String): CommandExecution = memScoped {
    val file = popen("exec 2>&1; $command", "r") ?: throw RuntimeException("Failed to execute command")

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
