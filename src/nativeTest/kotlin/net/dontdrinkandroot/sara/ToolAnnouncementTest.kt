package net.dontdrinkandroot.sara

import kotlin.test.Test
import kotlin.test.assertEquals

class ToolAnnouncementTest {

    @Test
    fun emptyArgsOmitColon() {
        assertEquals(
            "Executing tool 'read_file'",
            formatToolAnnouncement("read_file", "")
        )
    }

    @Test
    fun blankArgsOmitColon() {
        assertEquals(
            "Executing tool 'read_file'",
            formatToolAnnouncement("read_file", "   ")
        )
    }

    @Test
    fun shortArgsAreIncludedVerbatim() {
        assertEquals(
            "Executing tool 'exec_command': {\"command\":\"ls -la\"}",
            formatToolAnnouncement("exec_command", "{\"command\":\"ls -la\"}")
        )
    }

    @Test
    fun longArgsAreTruncated() {
        val args = "a".repeat(200)
        val announcement = formatToolAnnouncement("write_file", args, maxLength = 50)
        assertEquals("Executing tool 'write_file': ${"a".repeat(50)}...", announcement)
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals(
            "Executing tool 'exec_command': {\"command\":\"pwd\"}",
            formatToolAnnouncement("exec_command", "  {\"command\":\"pwd\"}  ")
        )
    }
}
