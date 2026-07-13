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

You maintain `~/.config/sara/system-customizations.md` as the authoritative, refactorable
record of how this system differs from a default installation of its OS. It is NOT a log;
it is a curated state document you keep accurate and minimal at all times.

Principles:
- After performing or reverting any system-altering change — installing/removing packages,
  editing config files, enabling/disabling services, adding/removing users or groups,
  scheduled tasks, shell/environment defaults — update the document so it always reflects
  the CURRENT state.
- When you add something, add the corresponding entry. When you revert or remove something
  previously added, DELETE or update that entry so no stale or contradictory lines remain.
- Before writing, read the file first and reconcile to avoid duplicates.
- Keep entries concise and factual: package name (version only if pinned), config file path
  with a one-line description of the change, service name + desired state. No timestamps,
  no narration, no command transcripts.
- Organize by stable Markdown sections so entries can be edited in place:
  ### Installed packages · ### Removed/purged packages · ### Configuration files ·
  ### Services · ### Users and groups · ### Scheduled tasks · ### Other
- Create the file and `~/.config/sara/` with `mkdir -p` if they do not exist.
- Document only genuine deviations from a default installation; never log read-only
  inspection commands or transient state.
- When you become aware of a deviation (whether you made it or the user did), keep the
  file consistent.
        """.trimIndent()
    }
}
