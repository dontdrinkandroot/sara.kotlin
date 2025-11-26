package net.dontdrinkandroot.sara.systemprompt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

class SystemInformationSystemPromptProvider : SystemPromptProvider {

    override fun provide(): String? = try {
        val sections = ChainedSystemPromptProvider(
            providers = listOf(
                GeneralSectionProvider(),
                MemorySectionProvider(),
                RootFsSectionProvider(),
                CpuSectionProvider(),
                CurrentDirectoryProvider(),
            ),
            separator = "\n\n"
        ).provide()

        sections.takeIf { it.isNotBlank() }?.let { nonEmptySections ->
            buildString {
                appendLine("## System Information")
                appendLine()
                append(nonEmptySections)
            }
        }
    } catch (e: Exception) {
        null
    }

    // ---------- General Section broken down into small providers ----------
    private inner class GeneralSectionProvider : SystemPromptProvider {
        @OptIn(ExperimentalForeignApi::class)
        override fun provide(): String? {
            val lines = ChainedSystemPromptProvider(
                providers = listOf(
                    StaticSystemPromptProvider("### General\n"),
                    DistributionProvider(),
                    KernelProvider(),
                    ArchitectureProvider(),
                    HostnameProvider(),
                    CurrentUserProvider(),
                    HomeDirectoryProvider(),
                    UptimeProvider(),
                    CurrentTimeProvider(),
                    TerminalProvider(),
                ),
                separator = "\n"
            ).provide().trimEnd()

            return lines.takeIf { it.isNotBlank() }
        }

        private inner class DistributionProvider : SystemPromptProvider {
            override fun provide(): String? {
                val osRelease =
                    cmd("cat /etc/os-release 2>/dev/null || cat /etc/lsb-release 2>/dev/null || echo 'Unknown distribution'")
                return osRelease?.let { content ->
                    val lines = content.lines()
                    val prettyName = lines.firstOrNull { it.startsWith("PRETTY_NAME=") }?.substringAfter("=")?.trim('"')
                    val name = lines.firstOrNull { it.startsWith("NAME=") }?.substringAfter("=")?.trim('"')
                    val version = lines.firstOrNull { it.startsWith("VERSION=") }?.substringAfter("=")?.trim('"')

                    buildString {
                        append("Distribution: ${prettyName ?: name ?: "Unknown"}")
                        if (version != null && prettyName?.contains(version) != true) append(" $version")
                    }
                }
            }
        }

        private inner class KernelProvider : SystemPromptProvider {
            override fun provide(): String? = cmd("uname -r")?.let { "Kernel: Linux $it" }
        }

        private inner class ArchitectureProvider : SystemPromptProvider {
            override fun provide(): String? = cmd("uname -m")?.let { "Architecture: $it" }
        }

        private inner class HostnameProvider : SystemPromptProvider {
            override fun provide(): String? = cmd("hostname")?.let { "Hostname: $it" }
        }

        @OptIn(ExperimentalForeignApi::class)
        private inner class CurrentUserProvider : SystemPromptProvider {
            override fun provide(): String? {
                val currentUser = getenv("USER")?.toKString() ?: cmd("whoami")
                return currentUser?.takeIf { it.isNotBlank() }?.let { "Current User: $it" }
            }
        }

        @OptIn(ExperimentalForeignApi::class)
        private inner class HomeDirectoryProvider : SystemPromptProvider {
            override fun provide(): String? =
                getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let { "Home Directory: $it" }
        }

        private inner class UptimeProvider : SystemPromptProvider {
            override fun provide(): String? = cmd("uptime -p 2>/dev/null || uptime")?.let { "Uptime: ${it.trim()}" }
        }

        private inner class CurrentTimeProvider : SystemPromptProvider {
            override fun provide(): String? = cmd("date")?.let { "Current Time: $it" }
        }

        @OptIn(ExperimentalForeignApi::class)
        private inner class TerminalProvider : SystemPromptProvider {
            override fun provide(): String? =
                getenv("TERM")?.toKString()?.takeIf { it.isNotBlank() }?.let { "Terminal: $it" }
        }
    }

    // ---------- Memory Section ----------
    private inner class MemorySectionProvider : SystemPromptProvider {
        override fun provide(): String? = cmd("free -h 2>/dev/null | head -2")?.let { memInfo ->
            val body = memInfo.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim() }
            if (body.isBlank()) null else buildString {
                appendLine("### Memory")
                appendLine()
                append(body)
            }
        }
    }

    // ---------- Root FS Section ----------
    private inner class RootFsSectionProvider : SystemPromptProvider {
        override fun provide(): String? = cmd("df -h / 2>/dev/null | tail -1")?.let { usage ->
            val u = usage.trim()
            if (u.isBlank()) null else buildString {
                appendLine("### Root Filesystem")
                appendLine()
                append(u)
            }
        }
    }

    // ---------- CPU Section ----------
    private inner class CpuSectionProvider : SystemPromptProvider {
        override fun provide(): String? =
            cmd("lscpu 2>/dev/null | grep -E '^(Architecture|CPU\\(s\\)|Model name|CPU MHz)' || grep -E '^(processor|model name|cpu MHz)' /proc/cpuinfo 2>/dev/null | head -4")?.let { cpuInfo ->
                val body = cpuInfo.lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim() }
                if (body.isBlank()) null else buildString {
                    appendLine("### CPU")
                    appendLine()
                    append(body)
                }
            }
    }
}
