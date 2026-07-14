package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import platform.posix.getenv

class HomeDirectoryProvider : SystemPromptProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun provide(): String? =
        getenv("HOME")?.toKString()?.takeIf { it.isNotBlank() }?.let { "Home Directory: $it" }
}
