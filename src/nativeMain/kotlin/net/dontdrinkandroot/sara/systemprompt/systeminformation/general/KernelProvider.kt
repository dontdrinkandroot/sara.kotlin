package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

class KernelProvider : SystemPromptProvider {
    override fun provide(): String? {
        val kernel = cmd("uname -r")
        val vendor = cmd("uname -s")
        return kernel?.let { "Kernel: ${vendor ?: "unknown"} $it" }
    }
}
