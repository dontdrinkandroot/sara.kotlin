package net.dontdrinkandroot.sara.systemprompt

import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.configuration.defaultConfigDir
import net.dontdrinkandroot.sara.extensions.exists
import net.dontdrinkandroot.sara.extensions.readString

/**
 * Maintains and injects `~/.config/sara/system-customizations.md`: a curated, refactorable
 * state document describing how this system deviates from a default installation. The
 * document's current contents are injected into the system prompt at session start so the
 * agent reconciles against the latest baseline before making further changes.
 */
class SystemCustomizationsProvider(
    private val configDir: Path = defaultConfigDir(),
) : SystemPromptProvider {

    override fun provide(): String {
        val filePath = Path("${configDir.toString().removeSuffix("/")}/$CUSTOMIZATIONS_FILE_NAME")
        val contents = runCatching { if (filePath.exists()) filePath.readString() else null }
            .getOrNull()
            ?.takeIf(String::isNotBlank)

        val currentContentsSection = when (contents) {
            null -> MISSING_MARKER
            else -> truncate(contents)
        }

        return buildString {
            append(INSTRUCTIONS)
            append("\n\n")
            append("Current contents of `~/.config/sara/system-customizations.md`:\n")
            append(currentContentsSection)
        }
    }

    private fun truncate(text: String): String =
        if (text.length <= MAX_CONTENT_LENGTH) text
        else text.take(MAX_CONTENT_LENGTH) + TRUNCATION_MARKER

    companion object {
        const val CUSTOMIZATIONS_FILE_NAME = "system-customizations.md"
        const val MISSING_MARKER = "(file does not exist yet — create it on first change)"
        const val MAX_CONTENT_LENGTH = 10000
        const val TRUNCATION_MARKER = "\n...[truncated]"

        val INSTRUCTIONS = """
## System Customizations

You maintain `~/.config/sara/system-customizations.md` as the curated, refactorable record
of how this system differs from a default installation — NOT a log. Keep it accurate and
minimal at all times.

- After any system-altering change (packages, config files, services, users/groups,
  scheduled tasks, shell/environment defaults), update the file to reflect the CURRENT
  state. When you revert a change, DELETE or update its entry so no stale lines remain.
- Before writing, read the file and reconcile to avoid duplicates. Create the file and
  `~/.config/sara/` (`mkdir -p`) on first change.
- Entries are concise and factual: package name (version only if pinned), config path +
  one-line description, service + desired state. No timestamps, narration, or transcripts.
- Use stable sections: ### Installed packages · ### Removed/purged packages ·
  ### Configuration files · ### Services · ### Users and groups · ### Scheduled tasks ·
  ### Other
- Document only genuine deviations; never log read-only inspection commands or transient
  state.
        """.trimIndent()
    }
}
