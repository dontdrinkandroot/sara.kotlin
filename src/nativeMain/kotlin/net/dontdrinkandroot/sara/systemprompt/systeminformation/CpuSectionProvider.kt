package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

/**
 * Emits a compact CPU summary. Prefers `lscpu`'s key fields; falls back to `/proc/cpuinfo`
 * (model name + core count) when `lscpu` is unavailable (e.g. minimal containers).
 * Per-core `processor` lines are intentionally excluded to keep the output compact and
 * unambiguous.
 */
class CpuSectionProvider : SystemPromptProvider {
    override fun provide(): String? = lscpuSummary() ?: cpuInfoSummary()

    private fun lscpuSummary(): String? {
        val body = cmd(
            "lscpu 2>/dev/null | grep -E '^(Architecture|CPU\\(s\\)|Model name|CPU MHz)'"
        ) ?: return null
        return section(body)
    }

    private fun cpuInfoSummary(): String? {
        val modelName = cmd("grep -m1 'model name' /proc/cpuinfo 2>/dev/null")
            ?.substringAfter(':')?.trim()
        val coreCount = cmd("grep -c '^processor' /proc/cpuinfo 2>/dev/null")
        return formatCpuInfo(modelName, coreCount)?.let { section(it) }
    }

    private fun section(body: String): String? {
        val cleaned = body.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim() }
        if (cleaned.isBlank()) return null
        return buildString {
            appendLine("### CPU")
            appendLine()
            append(cleaned)
        }.trimEnd()
    }
}

/**
 * Formats the `/proc/cpuinfo` fallback fields into `Model name: <m>` / `CPU(s): <n>` lines.
 * Returns null when both inputs are null/blank.
 */
internal fun formatCpuInfo(modelName: String?, coreCount: String?): String? {
    if (modelName.isNullOrBlank() && coreCount.isNullOrBlank()) return null
    return buildList {
        modelName?.takeIf { it.isNotBlank() }?.let { add("Model name: $it") }
        coreCount?.takeIf { it.isNotBlank() }?.let { add("CPU(s): $it") }
    }.joinToString("\n")
}
