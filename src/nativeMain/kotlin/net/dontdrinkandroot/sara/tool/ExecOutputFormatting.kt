package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.close
import platform.posix.mkstemp
import platform.posix.rename
import platform.posix.write

/**
 * Terminal status of a command: either an exit code or a death signal — never both, never
 * neither.
 */
data class ExitStatus(val code: Int?, val signal: String?)

/**
 * Head/tail excerpt of oversized command output. The complete output lives in [spillPath]
 * so the agent can paginate through it with `read_file`.
 */
data class TruncationInfo(
    val head: String,
    val tail: String,
    val omittedCount: Int,
    val spillPath: String,
)

/** Number of output characters delivered inline before head/tail truncation kicks in. */
private const val INLINE_OUTPUT_LIMIT = 20_000

private val signalNames = mapOf(
    1 to "SIGHUP", 2 to "SIGINT", 3 to "SIGQUIT", 4 to "SIGILL", 5 to "SIGTRAP",
    6 to "SIGABRT", 7 to "SIGBUS", 8 to "SIGFPE", 9 to "SIGKILL", 10 to "SIGUSR1",
    11 to "SIGSEGV", 12 to "SIGUSR2", 13 to "SIGPIPE", 14 to "SIGALRM", 15 to "SIGTERM",
    16 to "SIGSTKFLT", 17 to "SIGCHLD", 18 to "SIGCONT", 19 to "SIGSTOP", 20 to "SIGTSTP",
    21 to "SIGTTIN", 22 to "SIGTTOU", 23 to "SIGURG", 24 to "SIGXCPU", 25 to "SIGXFSZ",
    26 to "SIGVTALRM", 27 to "SIGPROF", 28 to "SIGWINCH", 29 to "SIGIO", 30 to "SIGPWR",
    31 to "SIGSYS",
)

/**
 * Decodes a `waitpid` status word as returned by `pclose` into an [ExitStatus]
 * (WIFEXITED → exit code, WIFSIGNALED → signal name, core-dump bit ignored).
 */
internal fun decodeWaitStatus(status: Int): ExitStatus {
    val signalNumber = status and 0x7f
    return when {
        signalNumber == 0 -> ExitStatus(code = (status shr 8) and 0xff, signal = null)
        signalNumber == 0x7f ->
            // WIFSTOPPED: cannot happen for a pclose'd child (no WUNTRACED); treat as exit code.
            ExitStatus(code = (status shr 8) and 0xff, signal = null)

        else -> ExitStatus(code = null, signal = signalName(signalNumber))
    }
}

internal fun signalName(signalNumber: Int): String =
    signalNames[signalNumber] ?: "SIG$signalNumber"

internal fun statusLine(exitStatus: ExitStatus): String = when {
    exitStatus.signal != null -> "[killed by signal: ${exitStatus.signal}]"
    else -> "[exit code: ${exitStatus.code}]"
}

/**
 * Splits [output] longer than [inlineLimit] into a head and a tail excerpt of at most
 * [halfLimit] characters each — snapped to line boundaries when a newline falls within the
 * excerpt window, kept raw otherwise — and spills the full output to a temporary file.
 * Returns `null` when the output fits inline (no spill file is created).
 */
internal fun truncateHeadTail(
    output: String,
    inlineLimit: Int = INLINE_OUTPUT_LIMIT,
    halfLimit: Int = inlineLimit / 2,
): TruncationInfo? {
    if (output.length <= inlineLimit) return null
    val head = snapToLineEnd(output, halfLimit)
    val tail = snapToLineStart(output, output.length - halfLimit)
    return TruncationInfo(
        head = head,
        tail = tail,
        omittedCount = output.length - head.length - tail.length,
        spillPath = writeSpillFile(output),
    )
}

/** Truncates at the last line end within the first [maxLength] characters (keeps [maxLength] when no newline fits). */
private fun snapToLineEnd(output: String, maxLength: Int): String {
    val window = output.substring(0, maxLength)
    val lastLineEnd = window.lastIndexOf('\n')
    return if (lastLineEnd < 0) window else window.substring(0, lastLineEnd + 1)
}

/** Truncates at the first line end at or after [start] (keeps from [start] when no newline follows). */
private fun snapToLineStart(output: String, start: Int): String {
    val lineEnd = output.indexOf('\n', start)
    return if (lineEnd < 0) output.substring(start) else output.substring(lineEnd + 1)
}

/**
 * Renders a command result for the LLM: body, blank line, status line — plus the truncation
 * marker and footer when the output was spilled. When [truncation] is present it is
 * authoritative: the head/tail excerpts are taken from it, not from [output].
 */
internal fun renderExecResult(
    output: String,
    exitStatus: ExitStatus,
    truncation: TruncationInfo?,
): String {
    val body = if (truncation == null) {
        output.trimEnd('\n').ifEmpty { "(no output)" }
    } else {
        listOfNotNull(
            truncation.head.trimEnd('\n').ifEmpty { null },
            truncationMarker(truncation),
            truncation.tail.trimEnd('\n').ifEmpty { null },
        ).joinToString("\n")
    }
    return body + buildString {
        append("\n\n")
        append(statusLine(exitStatus))
        if (truncation != null) {
            append(
                "\n[output truncated: ${truncation.omittedCount} characters omitted, " +
                    "full log: ${truncation.spillPath}]"
            )
        }
    }
}

private fun truncationMarker(truncation: TruncationInfo): String =
    "...[${truncation.omittedCount} characters omitted — " +
        "full log: ${truncation.spillPath}; use read_file to inspect]..."

/**
 * Writes [content] to a unique `/tmp/sara-exec-<random>.log` file and returns its path.
 * The file is created owner-only (mkstemp, 0600) and is never cleaned up by SARA — the
 * /tmp lifecycle takes care of it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun writeSpillFile(content: String): String = memScoped {
    val template = "/tmp/sara-exec-XXXXXX".cstr.getPointer(this)
    val fd = mkstemp(template)
    if (fd < 0) throw RuntimeException("Failed to create spill file for command output")
    try {
        val tempPath = template.toKString()
        writeAll(fd, content)
        val logPath = "$tempPath.log"
        if (rename(tempPath, logPath) != 0) throw RuntimeException("Failed to finalize spill file $logPath")
        logPath
    } finally {
        close(fd)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun writeAll(fd: Int, content: String) {
    val bytes = content.encodeToByteArray()
    var written = 0
    bytes.usePinned { pinned ->
        while (written < bytes.size) {
            val writtenNow = write(fd, pinned.addressOf(written), (bytes.size - written).convert())
            if (writtenNow < 0L) throw RuntimeException("Failed to write command output to spill file")
            written += writtenNow.toInt()
        }
    }
}
