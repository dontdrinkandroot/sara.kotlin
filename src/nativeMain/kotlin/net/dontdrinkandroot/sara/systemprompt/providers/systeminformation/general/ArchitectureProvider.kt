package net.dontdrinkandroot.sara.systemprompt.providers.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class ArchitectureProvider : SystemPromptProvider {
    override fun provide(): String? = cmd("uname -m")?.let { "Architecture: $it" }
}
