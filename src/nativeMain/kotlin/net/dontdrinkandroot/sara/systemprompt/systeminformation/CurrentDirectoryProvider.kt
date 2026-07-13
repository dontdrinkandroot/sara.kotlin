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
        val contents = cmd("ls -l 2>/dev/null || ls -1 2>/dev/null")
        return pwd?.takeIf { it.isNotBlank() }?.let { path ->
            buildString {
                appendLine("### Current Directory\n")
                appendLine("Path: $path")
                val listing = contents?.trim().orEmpty()
                if (listing.isNotEmpty()) {
                    appendLine()
                    appendLine("Contents (ls -l):")
                    append(listing)
                }
            }.trimEnd().takeIf { it.isNotBlank() }
        }
    }
}
