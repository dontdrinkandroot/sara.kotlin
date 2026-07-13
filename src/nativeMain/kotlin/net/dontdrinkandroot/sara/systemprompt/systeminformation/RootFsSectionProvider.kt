package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class RootFsSectionProvider : SystemPromptProvider {
    override fun provide(): String? = cmd("df -h / 2>/dev/null | head -2")?.let { usage ->
        val u = usage.trim()
        if (u.isBlank()) null else buildString {
            appendLine("### Root Filesystem")
            appendLine()
            append(u)
        }
    }
}
