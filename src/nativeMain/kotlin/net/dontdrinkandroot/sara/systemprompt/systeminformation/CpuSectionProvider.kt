package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

/**
 * Emits a compact CPU summary: the logical core count only (`CPU(s): N`). The model name
 * and per-core details are intentionally excluded — they rarely change a command decision
 * and are discoverable on demand. Prefers `lscpu`; falls back to `/proc/cpuinfo` when
 * `lscpu` is unavailable (e.g. minimal containers).
 */
class CpuSectionProvider : SystemPromptProvider {
    override fun provide(): String? = lscpuCount() ?: cpuInfoCount()

    private fun lscpuCount(): String? {
        val count = cmd("LC_ALL=C lscpu 2>/dev/null | grep -E '^CPU\\(s\\):' | awk '{print $2}'")
        return count?.takeIf { it.isNotBlank() }?.let { section(it) }
    }

    private fun cpuInfoCount(): String? {
        val count = cmd("grep -c '^processor' /proc/cpuinfo 2>/dev/null")
        return count?.takeIf { it.isNotBlank() }?.let { section(it) }
    }

    private fun section(count: String): String = buildString {
        appendLine("### CPU")
        appendLine()
        append("CPU(s): $count")
    }.trimEnd()
}
