package net.dontdrinkandroot.sara.systemprompt

import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.configuration.defaultConfigDir
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore

/**
 * Injects the current system customizations (`~/.config/sara/system-customizations.json`) into
 * the system prompt: a curated record of how this system deviates from a default installation,
 * rendered as `### <Section>` / `- [<id>] <entry>` so the agent can address entries by ID with
 * the customization tools.
 */
class SystemCustomizationsProvider(
    private val store: SystemCustomizationsStore,
) : SystemPromptProvider {

    constructor(configDir: Path = defaultConfigDir()) : this(SystemCustomizationsStore(configDir))

    override fun provide(): String {
        val rendered = runCatching { store.render(store.load()) }.getOrNull().orEmpty()

        val currentContentsSection = when {
            rendered.isBlank() -> MISSING_MARKER
            else -> truncate(rendered)
        }

        return buildString {
            append(INSTRUCTIONS)
            append("\n\n")
            append("Current system customizations:\n")
            append(currentContentsSection)
        }
    }

    private fun truncate(text: String): String {
        if (text.length <= MAX_CONTENT_LENGTH) return text
        val breakPoint = text.lastIndexOf('\n', startIndex = MAX_CONTENT_LENGTH)
        return if (breakPoint > 0) text.substring(0, breakPoint) + TRUNCATION_MARKER
        else text.take(MAX_CONTENT_LENGTH) + TRUNCATION_MARKER
    }

    companion object {
        const val MISSING_MARKER = "(no customizations recorded yet)"
        const val MAX_CONTENT_LENGTH = 10000
        const val TRUNCATION_MARKER = "\n...[truncated]"

        val INSTRUCTIONS = """
## System Customizations

You maintain the curated record of how this system differs from a default installation via the
customization tools (`add_customization`, `remove_customization`, `replace_customization`) —
NOT a log. Keep it accurate and minimal at all times. Never record it manually via file edits.

- After any system-altering change (packages, config files, services, users/groups, scheduled
  tasks, shell/environment defaults), record it with `add_customization`. Entries are addressed
  by the IDs shown below; use `replace_customization` to update an entry and
  `remove_customization` when a change is reverted, so no stale entries remain.
- Entries are concise and factual: package name (version only if pinned), config path +
  one-line description, service + desired state. No timestamps, narration, or transcripts.
- Document only genuine deviations; never record read-only inspection commands or transient
  state.
        """.trimIndent()
    }
}
