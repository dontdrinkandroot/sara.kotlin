package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

/**
 * Probes whether `sudo` is installed and whether it can be used non-interactively.
 * This lets the agent know in advance whether a privileged command will hang on a
 * password prompt (which the persona forbids) or run unattended.
 */
class SudoProvider : SystemPromptProvider {
    override fun provide(): String? {
        val probe = cmd(
            "command -v sudo >/dev/null 2>&1 && { sudo -n true 2>/dev/null && echo passwordless || echo requires-password; } || echo unavailable"
        )
        return formatSudoStatus(probe)
    }
}

/**
 * Maps the [SudoProvider] probe output to a `Sudo: <status>` line.
 * Recognized statuses: `passwordless`, `requires-password`, `unavailable`.
 */
internal fun formatSudoStatus(raw: String?): String? {
    return when (val status = raw?.trim()?.lowercase()) {
        "passwordless", "requires-password", "unavailable" -> "Sudo: $status"
        else -> null
    }
}
