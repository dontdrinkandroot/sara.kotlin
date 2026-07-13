package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class TimezoneProvider : SystemPromptProvider {
    override fun provide(): String? =
        cmd("cat /etc/timezone 2>/dev/null || date +%Z 2>/dev/null")
            ?.takeIf { it.isNotBlank() }
            ?.let { "Timezone: $it" }
}
