package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadAverageProviderTest {

    @Test
    fun parsesProcLoadavgWithExtraFields() {
        // /proc/loadavg format: "0.42 0.51 0.47 1/234 5678"
        assertEquals("Load average: 0.42 0.51 0.47", formatLoadAverage("0.42 0.51 0.47 1/234 5678"))
    }

    @Test
    fun parsesSysctlTriple() {
        assertEquals("Load average: 1.5 2.0 1.8", formatLoadAverage("1.5 2.0 1.8"))
    }

    @Test
    fun collapsesRepeatedWhitespace() {
        assertEquals("Load average: 0.1 0.2 0.3", formatLoadAverage("  0.1   0.2   0.3  extra  "))
    }

    @Test
    fun returnsNullForBlankInput() {
        assertNull(formatLoadAverage("   "))
        assertNull(formatLoadAverage(""))
    }
}
