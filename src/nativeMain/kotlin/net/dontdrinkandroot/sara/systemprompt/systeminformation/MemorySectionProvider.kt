package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class MemorySectionProvider : SystemPromptProvider {
    override fun provide(): String? =
        cmd("LC_ALL=C free -h 2>/dev/null | head -3")?.let(::formatMemory)
}

/**
 * Parses `free -h` output (header + Mem + Swap) into a single compact line:
 * `Memory: <total> total, <available> available`. Returns null when the Mem row or its
 * total/available columns cannot be found.
 */
internal fun formatMemory(raw: String): String? {
    val memLine = raw.lines().firstOrNull { it.trimStart().startsWith("Mem:") } ?: return null
    val cols = memLine.trim().split(Regex("\\s+"))
    if (cols.size < 4) return null
    val total = cols.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    val available = cols.getOrNull(6) ?: cols.getOrNull(3) ?: return null
    return "Memory: $total total, $available available"
}
