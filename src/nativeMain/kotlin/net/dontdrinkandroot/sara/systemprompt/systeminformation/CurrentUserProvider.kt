package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd
import platform.posix.getenv

class CurrentUserProvider : SystemPromptProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun provide(): String? {
        val currentUser = getenv("USER")?.toKString() ?: cmd("whoami")
        return currentUser?.takeIf { it.isNotBlank() }?.let { "Current User: $it" }
    }
}
