package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.StaticSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider

/**
 * Assembles the "### General" block: a compact one-line-per-field summary of the host
 * (distribution, kernel, architecture, hostname, current user, home directory, timezone,
 * shell, locale, load average). Each leaf provider is isolated via [safeProvide], so a
 * missing field never suppresses the rest.
 */
class GeneralSectionProvider : SystemPromptProvider {
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
                TimezoneProvider(),
                ShellProvider(),
                LocaleProvider(),
                LoadAverageProvider(),
            ),
            separator = "\n"
        ).provide().trimEnd()

        return lines.takeIf { it.isNotBlank() }
    }
}
