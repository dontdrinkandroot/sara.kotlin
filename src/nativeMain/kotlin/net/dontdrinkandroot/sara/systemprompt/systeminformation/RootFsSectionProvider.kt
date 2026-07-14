package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class RootFsSectionProvider : SystemPromptProvider {
    override fun provide(): String? =
        cmd("LC_ALL=C df -h / 2>/dev/null | head -2")?.let(::formatRootFs)
}

/**
 * Parses `df -h /` output (header + data row) into a single compact line:
 * `Root filesystem: <size>, <use%> used`. Returns null when the data row or its
 * size / use-percent columns cannot be found.
 */
internal fun formatRootFs(raw: String): String? {
    val dataLine = raw.lines().getOrNull(1) ?: return null
    val cols = dataLine.trim().split(Regex("\\s+"))
    val size = cols.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
    val usePercent = cols.getOrNull(4)?.takeIf { it.isNotBlank() && it.endsWith('%') } ?: return null
    return "Root filesystem: $size, $usePercent used"
}
