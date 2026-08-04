package net.dontdrinkandroot.sara.systemprompt.providers

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider

/**
 * Dynamically renders the `## Web tool usage` section based on which web tools are actually
 * registered: Exa tools (`exa_search`, `exa_contents`), Searxng (`web_search`), and/or the
 * built-in `web_fetch`. Returns null when no web tools are available so the section is omitted.
 */
class WebToolUsageProvider(
    private val webFetchEnabled: Boolean,
    private val webSearchEnabled: Boolean,
    private val exaEnabled: Boolean,
) : SystemPromptProvider {

    override fun provide(): String? {
        if (!webFetchEnabled && !webSearchEnabled && !exaEnabled) return null

        return buildString {
            append("## Web tool usage\n\n")
            append("When a topic is unfamiliar, fast-moving, or version-specific (recent library APIs, ")
            append("package names, CLI flags, distro quirks, current best practices), prefer a quick ")
            append(preferredTools())
            append(" to ground your answer in up-to-date sources instead of relying on memory. ")
            append("Skip this for stable, well-known facts or when the user's system already provides the answer locally.")
            if (exaEnabled && webSearchEnabled) {
                append("\n")
                append("If the Exa tools are available, prefer them over `web_search` for higher-quality ")
                append("results and cleaner content extraction.")
            }
        }.trim()
    }

    private fun preferredTools(): String = when {
        exaEnabled -> "`exa_search` or `exa_contents`"
        webSearchEnabled && webFetchEnabled -> "`web_search` or `web_fetch`"
        webSearchEnabled -> "`web_search`"
        else -> "`web_fetch`"
    }
}
