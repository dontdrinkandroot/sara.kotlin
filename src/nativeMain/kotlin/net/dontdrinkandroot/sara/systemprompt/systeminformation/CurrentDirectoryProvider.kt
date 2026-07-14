package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
class CurrentDirectoryProvider : SystemPromptProvider {
    override fun provide(): String? {
        val pwd = getenv("PWD")?.toKString() ?: cmd("pwd")
        val contents = cmd("LC_ALL=C ls -lA 2>/dev/null || LC_ALL=C ls -1A 2>/dev/null")
        return pwd?.takeIf { it.isNotBlank() }?.let { path ->
            buildString {
                appendLine("### Current Directory\n")
                appendLine("Path: $path")
                val listing = contents?.trim().orEmpty()
                if (listing.isNotEmpty()) {
                    appendLine()
                    appendLine("Contents (ls -lA):")
                    append(capListing(listing))
                }
            }.trimEnd().takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Caps a directory listing to [MAX_LISTING_LINES] lines. When the listing exceeds the cap,
 * the first [MAX_LISTING_LINES] lines are kept and a `... (N more entries omitted)` tail
 * is appended so the agent knows entries were elided. The leading `total NNN` summary line
 * from `ls -l` (if present) does not count against the cap.
 */
internal fun capListing(listing: String): String {
    val lines = listing.lines()
    if (lines.size <= MAX_LISTING_LINES) return listing
    val head = lines.take(MAX_LISTING_LINES)
    val omitted = lines.size - MAX_LISTING_LINES
    return head.joinToString("\n") + "\n... ($omitted more entries omitted)"
}

private const val MAX_LISTING_LINES = 40
