package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CpuInfoFormatterTest {

    @Test
    fun formatsBothModelAndCores() {
        assertEquals("Model name: Intel i7\nCPU(s): 8", formatCpuInfo("Intel i7", "8"))
    }

    @Test
    fun formatsModelOnlyWhenCoresBlank() {
        assertEquals("Model name: AMD Ryzen", formatCpuInfo("AMD Ryzen", null))
        assertEquals("Model name: AMD Ryzen", formatCpuInfo("AMD Ryzen", ""))
    }

    @Test
    fun formatsCoresOnlyWhenModelBlank() {
        assertEquals("CPU(s): 16", formatCpuInfo(null, "16"))
        assertEquals("CPU(s): 16", formatCpuInfo("", "16"))
    }

    @Test
    fun returnsNullWhenBothBlank() {
        assertNull(formatCpuInfo(null, null))
        assertNull(formatCpuInfo("", ""))
        assertNull(formatCpuInfo("   ", "  "))
    }
}
