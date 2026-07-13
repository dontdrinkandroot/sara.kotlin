package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import platform.posix.getenv

class LocaleProvider : SystemPromptProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun provide(): String? =
        (getenv("LC_ALL")?.toKString() ?: getenv("LANG")?.toKString())
            ?.takeIf { it.isNotBlank() }
            ?.let { "Locale: $it" }
}
