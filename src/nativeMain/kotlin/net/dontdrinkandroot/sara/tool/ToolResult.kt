package net.dontdrinkandroot.sara.tool

/**
 * Result of a tool execution.
 */
sealed class ToolResult {
    data class Success(val output: String) : ToolResult()

    /**
     * Result of a command execution: the raw output, its terminal status, and — for
     * oversized output — the head/tail truncation whose full log lives in a spill file.
     */
    data class CommandResult(
        val output: String,
        val exitStatus: ExitStatus,
        val truncation: TruncationInfo? = null,
    ) : ToolResult()

    data class Error(val message: String) : ToolResult()
    data class Cancelled(val reason: String) : ToolResult()

    fun toContentString(): String = when (this) {
        is Success -> output
        is CommandResult -> renderExecResult(output, exitStatus, truncation)
        is Error -> "Error: $message"
        is Cancelled -> "Cancelled: $reason"
    }
}
