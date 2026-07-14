package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import platform.posix.getenv

class ShellProvider : SystemPromptProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun provide(): String? =
        getenv("SHELL")?.toKString()?.takeIf { it.isNotBlank() }?.let { "Shell: $it" }
}
