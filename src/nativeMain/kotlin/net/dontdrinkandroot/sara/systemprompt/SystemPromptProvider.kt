package net.dontdrinkandroot.sara.systemprompt

import net.dontdrinkandroot.sara.tool.executeCommandSafe

interface SystemPromptProvider {
    fun provide(): String?
}

class ChainedSystemPromptProvider(
    private val providers: List<SystemPromptProvider>,
    private val separator: String = ""
) : SystemPromptProvider {
    override fun provide(): String {
        return providers
            .mapNotNull { it.provide() }
            .joinToString(separator)
    }
}

fun cmd(command: String): String? = executeCommandSafe(command)?.trim()?.takeIf { it.isNotEmpty() }
