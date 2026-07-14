package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.systeminformation.general.formatSudoStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SudoStatusFormatterTest {

    @Test
    fun formatsPasswordless() {
        assertEquals("Sudo: passwordless", formatSudoStatus("passwordless"))
    }

    @Test
    fun formatsRequiresPassword() {
        assertEquals("Sudo: requires-password", formatSudoStatus("requires-password"))
    }

    @Test
    fun formatsUnavailable() {
        assertEquals("Sudo: unavailable", formatSudoStatus("unavailable"))
    }

    @Test
    fun trimsAndNormalizesCase() {
        assertEquals("Sudo: passwordless", formatSudoStatus("  Passwordless  "))
    }

    @Test
    fun returnsNullForUnexpectedInput() {
        assertNull(formatSudoStatus("something-else"))
        assertNull(formatSudoStatus(""))
        assertNull(formatSudoStatus(null))
    }
}
