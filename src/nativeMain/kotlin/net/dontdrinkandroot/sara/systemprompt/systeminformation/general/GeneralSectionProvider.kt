package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.StaticSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider

/**
 * Assembles the "### General" block: a compact one-line-per-field summary of the host
 * (date, distribution, architecture, package manager, sudo status, current user, home
 * directory, timezone, shell, locale). Each leaf provider is isolated via [safeProvide],
 * so a missing field never suppresses the rest.
 */
class GeneralSectionProvider : SystemPromptProvider {
    override fun provide(): String? {
        val lines = ChainedSystemPromptProvider(
            providers = listOf(
                StaticSystemPromptProvider("### General\n"),
                DateTimeProvider(),
                DistributionProvider(),
                ArchitectureProvider(),
                PackageManagerProvider(),
                SudoProvider(),
                CurrentUserProvider(),
                HomeDirectoryProvider(),
                TimezoneProvider(),
                ShellProvider(),
                LocaleProvider(),
            ),
            separator = "\n"
        ).provide().trimEnd()

        return lines.takeIf { it.isNotBlank() }
    }
}
