package net.dontdrinkandroot.sara.systemprompt

import net.dontdrinkandroot.sara.tool.executeCommandSafe

interface SystemPromptProvider {
    fun provide(): String?
}

/**
 * Wraps [provide] so a throwing sub-provider degrades to "section omitted" instead of
 * blanking every sibling in the chain. Used by [ChainedSystemPromptProvider] to isolate
 * failures (e.g. a missing `lscpu` must not suppress the Memory section).
 */
fun SystemPromptProvider.safeProvide(): String? = try {
    provide()
} catch (e: Exception) {
    null
}

class ChainedSystemPromptProvider(
    private val providers: List<SystemPromptProvider>,
    private val separator: String = ""
) : SystemPromptProvider {
    override fun provide(): String {
        return providers
            .mapNotNull { it.safeProvide() }
            .joinToString(separator)
    }
}

fun cmd(command: String): String? = executeCommandSafe(command)?.trim()?.takeIf { it.isNotEmpty() }
