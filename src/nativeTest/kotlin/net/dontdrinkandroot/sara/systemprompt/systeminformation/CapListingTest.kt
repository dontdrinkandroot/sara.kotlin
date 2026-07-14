package net.dontdrinkandroot.sara.systemprompt.systeminformation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapListingTest {

    @Test
    fun returnsListingUnchangedWhenAtOrUnderCap() {
        val listing = (1..40).joinToString("\n") { "entry-$it" }
        assertEquals(listing, capListing(listing))
    }

    @Test
    fun truncatesAndAppendsOmissionCountWhenOverCap() {
        val listing = (1..50).joinToString("\n") { "entry-$it" }
        val result = capListing(listing)
        val lines = result.lines()
        // 40 kept lines + 1 omission marker line.
        assertEquals(41, lines.size)
        assertEquals("entry-40", lines[39])
        assertTrue(lines[40].startsWith("... (10 more entries omitted)"))
    }

    @Test
    fun reportsCorrectOmissionCount() {
        val listing = (1..100).joinToString("\n") { "entry-$it" }
        val result = capListing(listing)
        assertTrue(result.endsWith("... (60 more entries omitted)"))
    }
}
