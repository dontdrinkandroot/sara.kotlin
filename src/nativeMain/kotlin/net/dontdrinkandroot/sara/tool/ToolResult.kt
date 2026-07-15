package net.dontdrinkandroot.sara.tool

/**
 * Result of a tool execution.
 */
sealed class ToolResult {
    data class Success(val output: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
    data class Cancelled(val reason: String) : ToolResult()

    fun toContentString(): String = when (this) {
        is Success -> output
        is Error -> "Error: $message"
        is Cancelled -> "Cancelled: $reason"
    }
}
