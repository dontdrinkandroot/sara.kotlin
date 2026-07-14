package net.dontdrinkandroot.sara.systemprompt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import net.dontdrinkandroot.sara.systemprompt.systeminformation.SystemInformationSystemPromptProvider
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertNotNull

@OptIn(ExperimentalForeignApi::class)
class SystemPromptDumpTest {

    @Test
    fun dumpSystemPrompt() {
        val provider = ChainedSystemPromptProvider(
            listOf(
                SaraSystemPromptProvider(),
                SystemCustomizationsProvider(),
                SystemInformationSystemPromptProvider()
            ),
            separator = "\n\n"
        )
        val prompt = provider.provide()
        assertNotNull(prompt)

        val isCI = getenv("CI")?.toKString() == "true"
        if (!isCI) {
            println("=== SYSTEM PROMPT START ===")
            println(prompt)
            println("=== SYSTEM PROMPT END ===")
            println("Length: ${prompt.length} characters")
        }
    }
}
