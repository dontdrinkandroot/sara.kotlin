package net.dontdrinkandroot.sara.systemprompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChainedSystemPromptProviderTest {

    private class StaticProvider(private val value: String?) : SystemPromptProvider {
        override fun provide(): String? = value
    }

    private class ThrowingProvider : SystemPromptProvider {
        override fun provide(): String? = throw RuntimeException("boom")
    }

    @Test
    fun joinsNonEmptyProvidersWithSeparator() {
        val chain = ChainedSystemPromptProvider(
            providers = listOf(StaticProvider("a"), StaticProvider("b")),
            separator = "\n"
        )
        assertEquals("a\nb", chain.provide())
    }

    @Test
    fun skipsNullProviders() {
        val chain = ChainedSystemPromptProvider(
            providers = listOf(StaticProvider("a"), StaticProvider(null), StaticProvider("b")),
            separator = "|"
        )
        assertEquals("a|b", chain.provide())
    }

    @Test
    fun isolatesThrowingProviderSoSiblingsSurvive() {
        val chain = ChainedSystemPromptProvider(
            providers = listOf(
                StaticProvider("before"),
                ThrowingProvider(),
                StaticProvider("after")
            ),
            separator = "\n"
        )
        assertEquals("before\nafter", chain.provide())
    }

    @Test
    fun returnsEmptyWhenAllFail() {
        val chain = ChainedSystemPromptProvider(
            providers = listOf(ThrowingProvider(), StaticProvider(null)),
            separator = "\n"
        )
        assertEquals("", chain.provide())
    }

    @Test
    fun safeProvideSwallowsExceptionsAndReturnsNull() {
        assertNull(ThrowingProvider().safeProvide())
        assertEquals("ok", StaticProvider("ok").safeProvide())
    }
}
