package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider

/**
 * Injects a "## System Information" block at session start: General / Memory / Root FS /
 * CPU / Current Directory. Every leaf provider is isolated via [safeProvide] (called by
 * [ChainedSystemPromptProvider]), so a single failing section — e.g. `lscpu` missing or
 * `free` unavailable — is silently omitted rather than blanking the whole block.
 */
class SystemInformationSystemPromptProvider : SystemPromptProvider {

    override fun provide(): String? {
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

        return sections.takeIf { it.isNotBlank() }?.let { nonEmptySections ->
            buildString {
                appendLine("## System Information")
                appendLine()
                append(nonEmptySections)
            }
        }
    }
}
