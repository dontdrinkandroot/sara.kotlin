package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemoryFormatterTest {

    @Test
    fun parsesFullFreeOutput() {
        val raw = """
            total        used        free      shared  buff/cache   available
            Mem:            30Gi        17Gi       364Mi       129Mi        13Gi        13Gi
            Swap:           59Gi        28Ki        59Gi
        """.trimIndent()
        assertEquals("Memory: 30Gi total, 13Gi available", formatMemory(raw))
    }

    @Test
    fun fallsBackToFreeColumnWhenAvailableAbsent() {
        // Some minimal `free` builds omit the `available` column (6th field).
        val raw = """
            total        used        free      shared  buff/cache
            Mem:            30Gi        17Gi       364Mi       129Mi        13Gi
        """.trimIndent()
        assertEquals("Memory: 30Gi total, 364Mi available", formatMemory(raw))
    }

    @Test
    fun returnsNullWhenNoMemLine() {
        assertNull(formatMemory("total used free\nSwap: 59Gi 28Ki 59Gi"))
    }

    @Test
    fun returnsNullWhenColumnsMissing() {
        assertNull(formatMemory("Mem:"))
    }
}
