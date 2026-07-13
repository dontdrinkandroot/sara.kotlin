package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals

class DistributionParserTest {

    @Test
    fun parsesPrettyNameAndVersion() {
        val osRelease = """
            NAME="Debian GNU/Linux"
            VERSION_ID=12
            VERSION="12 (bookworm)"
            PRETTY_NAME="Debian GNU/Linux 12 (bookworm)"
            ID=debian
        """.trimIndent()

        // Version is already part of PRETTY_NAME, so it must not be appended again.
        assertEquals("Distribution: Debian GNU/Linux 12 (bookworm)", parseDistribution(osRelease))
    }

    @Test
    fun appendsVersionWhenNotInPrettyName() {
        val osRelease = """
            NAME="Ubuntu"
            VERSION="22.04 LTS"
            PRETTY_NAME="Ubuntu"
        """.trimIndent()

        assertEquals("Distribution: Ubuntu 22.04 LTS", parseDistribution(osRelease))
    }

    @Test
    fun fallsBackToNameWhenPrettyNameAbsent() {
        val osRelease = """
            NAME="Arch Linux"
            VERSION=""
        """.trimIndent()

        assertEquals("Distribution: Arch Linux", parseDistribution(osRelease))
    }

    @Test
    fun reportsUnknownWhenAllAbsent() {
        assertEquals("Distribution: Unknown", parseDistribution(""))
        assertEquals("Distribution: Unknown", parseDistribution("UNRELATED=foo\n"))
    }
}
