package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class DistributionProvider : SystemPromptProvider {
    override fun provide(): String? {
        val osRelease =
            cmd("cat /etc/os-release 2>/dev/null || cat /etc/lsb-release 2>/dev/null || echo 'Unknown distribution'")
        return osRelease?.let(::parseDistribution)
    }
}

/**
 * Parses `os-release` / `lsb-release` content into a compact `Distribution: <name> <version>` line.
 * The version is appended only when it is not already part of the pretty name.
 */
internal fun parseDistribution(content: String): String {
    val lines = content.lines()
    val prettyName = lines.firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter("=")?.trim('"')
    val name = lines.firstOrNull { it.startsWith("NAME=") }?.substringAfter("=")?.trim('"')
    val version = lines.firstOrNull { it.startsWith("VERSION=") }?.substringAfter("=")?.trim('"')

    return buildString {
        append("Distribution: ${prettyName ?: name ?: "Unknown"}")
        if (!version.isNullOrBlank() && prettyName?.contains(version) != true) append(" $version")
    }
}
