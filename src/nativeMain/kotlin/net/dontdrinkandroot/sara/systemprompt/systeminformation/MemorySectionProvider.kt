package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class MemorySectionProvider : SystemPromptProvider {
    override fun provide(): String? = cmd("free -h 2>/dev/null | head -3")?.let { memInfo ->
        val body = memInfo.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim() }
        if (body.isBlank()) null else buildString {
            appendLine("### Memory")
            appendLine()
            append(body)
        }
    }
}
