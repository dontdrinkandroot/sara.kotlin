package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RootFsFormatterTest {

    @Test
    fun parsesDfOutput() {
        val raw = """
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/nvme1n1p5   89G   38G   47G  45% /
        """.trimIndent()
        assertEquals("Root filesystem: 89G, 45% used", formatRootFs(raw))
    }

    @Test
    fun returnsNullWhenNoDataLine() {
        assertNull(formatRootFs("Filesystem      Size  Used Avail Use% Mounted on"))
    }

    @Test
    fun returnsNullWhenUsePercentMissing() {
        // Use% column absent -> cannot report usage.
        assertNull(formatRootFs("Filesystem      Size  Used Avail\n/dev/sda1   50G   20G   30G"))
    }
}
