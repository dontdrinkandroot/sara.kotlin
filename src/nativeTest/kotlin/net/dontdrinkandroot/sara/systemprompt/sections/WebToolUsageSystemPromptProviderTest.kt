package net.dontdrinkandroot.sara.systemprompt.sections

import kotlin.test.*

class WebToolUsageSystemPromptProviderTest {

    private fun provider(
        webFetch: Boolean = false,
        webSearch: Boolean = false,
        exa: Boolean = false,
    ) = WebToolUsageSystemPromptProvider(webFetch, webSearch, exa)

    @Test
    fun returnsNullWhenNoWebToolsAvailable() {
        assertNull(provider().provide())
    }

    @Test
    fun listsWebFetchOnlyWhenOnlyWebFetchIsAvailable() {
        val section = provider(webFetch = true).provide()
        assertNotNull(section)
        assertTrue(section.startsWith("## Web tool usage"))
        assertContains(section, "`web_fetch`")
        assertFalse(section.contains("web_search"))
        assertFalse(section.contains("exa_"))
    }

    @Test
    fun listsWebSearchAndWebFetchWhenSearxngIsEnabled() {
        val section = provider(webFetch = true, webSearch = true).provide()
        assertNotNull(section)
        assertContains(section, "`web_search` or `web_fetch`")
        assertFalse(section.contains("exa_"))
    }

    @Test
    fun listsExaToolsOnlyWhenExaIsEnabled() {
        val section = provider(exa = true).provide()
        assertNotNull(section)
        assertContains(section, "`exa_search` or `exa_contents`")
        assertFalse(section.contains("web_search"))
        assertFalse(section.contains("web_fetch"))
    }

    @Test
    fun prefersExaOverWebSearchWhenBothAreEnabled() {
        val section = provider(exa = true, webSearch = true).provide()
        assertNotNull(section)
        assertContains(section, "`exa_search` or `exa_contents`")
        assertContains(section, "prefer them over `web_search`")
        assertFalse(section.contains("web_fetch"))
    }

    @Test
    fun omitsExaPreferenceHintWhenWebSearchIsUnavailable() {
        val section = provider(exa = true).provide()
        assertNotNull(section)
        assertFalse(section.contains("prefer them over `web_search`"))
    }
}
