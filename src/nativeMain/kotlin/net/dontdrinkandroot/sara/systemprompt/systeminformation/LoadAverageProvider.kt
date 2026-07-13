package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class LoadAverageProvider : SystemPromptProvider {
    override fun provide(): String? =
        cmd("cat /proc/loadavg 2>/dev/null || sysctl -n vm.loadavg 2>/dev/null | cut -c2-")
            ?.let(::formatLoadAverage)
}

/**
 * Formats raw load-average output (e.g. `0.42 0.51 0.47 1/234 5678` from /proc/loadavg, or
 * `0.42 0.51 0.47` from sysctl) as `Load average: <1m> <5m> <15m>`.
 */
internal fun formatLoadAverage(raw: String): String? {
    val firstThree = raw.trim().split(' ').filter { it.isNotBlank() }.take(3).joinToString(" ")
    return firstThree.takeIf { it.isNotBlank() }?.let { "Load average: $it" }
}
