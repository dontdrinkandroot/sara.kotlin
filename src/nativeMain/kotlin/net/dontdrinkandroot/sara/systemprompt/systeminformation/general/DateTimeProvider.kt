package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

/**
 * Emits the current local date and time in ISO 8601 form so the agent can reason about
 * log timestamps, scheduling, and `date` commands without an extra tool call.
 */
class DateTimeProvider : SystemPromptProvider {
    override fun provide(): String? =
        cmd("date -Is 2>/dev/null")?.takeIf { it.isNotBlank() }?.let { "Date: $it" }
}
