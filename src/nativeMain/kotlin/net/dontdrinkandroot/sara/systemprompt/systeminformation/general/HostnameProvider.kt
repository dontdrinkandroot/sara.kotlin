package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import kotlinx.cinterop.*
import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd
import platform.posix.gethostname

class HostnameProvider : SystemPromptProvider {
    @OptIn(ExperimentalForeignApi::class)
    override fun provide(): String? = memScoped {
        val buffer = allocArray<ByteVar>(BUFFER_SIZE)
        val result = gethostname(buffer, BUFFER_SIZE.toULong())
        if (result == 0) {
            buffer.toKString().takeIf(String::isNotBlank)?.let { "Hostname: $it" }
        } else {
            cmd("hostname")?.let { "Hostname: $it" }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 256
    }
}
