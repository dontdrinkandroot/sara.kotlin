package net.dontdrinkandroot.sara.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PasteMarkerTest {

    private fun feed(vararg chars: Char): List<PasteMarkerStep> {
        var pending = PendingPasteMarker.NONE
        return chars.map { c ->
            val step = nextPasteMarkerState(c, pending)
            pending = when (step) {
                is PasteMarkerStep.MaybeStart -> step.next
                else -> PendingPasteMarker.NONE
            }
            step
        }
    }

    @Test
    fun plainCharactersPassThrough() {
        val steps = feed('h', 'i')
        assertIs<PasteMarkerStep.PassThrough>(steps[0])
        assertEquals('h', (steps[0] as PasteMarkerStep.PassThrough).char)
        assertIs<PasteMarkerStep.PassThrough>(steps[1])
    }

    @Test
    fun fullStartMarkerIsRecognized() {
        val steps = feed(ESC_CHAR, '[', '2', '0', '0', '~')
        steps.take(5).forEach { assertIs<PasteMarkerStep.MaybeStart>(it) }
        assertIs<PasteMarkerStep.Start>(steps[5])
    }

    @Test
    fun partialMarkerStaysPending() {
        val steps = feed(ESC_CHAR, '[', '2', '0', '0')
        steps.take(4).forEach { assertIs<PasteMarkerStep.MaybeStart>(it) }
        assertIs<PasteMarkerStep.MaybeStart>(steps[4])
    }

    @Test
    fun partialMarkerFollowedByOtherCharIsRejectedAndEmitted() {
        // ESC[201 matches the END marker shape, so it can never become a start marker;
        // the x reveals the pending prefix as regular text.
        val steps = feed(ESC_CHAR, '[', '2', '0', '1', 'x')
        val reject = steps[4]
        assertIs<PasteMarkerStep.Reject>(reject)
        assertEquals("${ESC_CHAR}[201", reject.emitted)
        assertIs<PasteMarkerStep.PassThrough>(steps[5])
        assertEquals('x', (steps[5] as PasteMarkerStep.PassThrough).char)
    }

    @Test
    fun otherEscapeSequencesAreRejected() {
        val steps = feed(ESC_CHAR, 'O', 'P')
        assertIs<PasteMarkerStep.Reject>(steps[1])
        assertEquals("${ESC_CHAR}O", (steps[1] as PasteMarkerStep.Reject).emitted)
        assertIs<PasteMarkerStep.PassThrough>(steps[2])
        assertEquals('P', (steps[2] as PasteMarkerStep.PassThrough).char)
    }

    @Test
    fun escapedBracketThenOtherDigitIsRejected() {
        val steps = feed(ESC_CHAR, '[', '3', '~')
        assertIs<PasteMarkerStep.Reject>(steps[2])
        assertEquals("${ESC_CHAR}[3", (steps[2] as PasteMarkerStep.Reject).emitted)
        assertIs<PasteMarkerStep.PassThrough>(steps[3])
        assertEquals('~', (steps[3] as PasteMarkerStep.PassThrough).char)
    }

    @Test
    fun pendingPrefixFromOtherSequenceDoesNotStayStuck() {
        // ESC[200 followed by a space is not a start marker: the pending ESC[200 prefix
        // must be flushed as text instead of waiting forever for '~'.
        val steps = feed(ESC_CHAR, '[', '2', '0', '0', ' ', 'x')
        assertIs<PasteMarkerStep.Reject>(steps[5])
        assertEquals("${ESC_CHAR}[200 ", (steps[5] as PasteMarkerStep.Reject).emitted)
        assertIs<PasteMarkerStep.PassThrough>(steps[6])
    }

    @Test
    fun endMarkerMatchingAccumulatesAndMatches() {
        // Feed ESC[201~ through the recognizer: as long as the start-marker lookahead
        // matches, the end-marker matcher must report ACCUMULATE; the final '~' completes
        // the end marker.
        var pending = PendingPasteMarker.NONE
        val matches = listOf(ESC_CHAR, '[', '2', '0', '1', '~').map { c ->
            val match = matchPasteEndMarker(c, pending)
            val step = nextPasteMarkerState(c, pending)
            pending = when (step) {
                is PasteMarkerStep.MaybeStart -> step.next
                else -> PendingPasteMarker.NONE
            }
            match
        }
        assertEquals(
            listOf(
                PasteEndMatch.ACCUMULATE,
                PasteEndMatch.ACCUMULATE,
                PasteEndMatch.ACCUMULATE,
                PasteEndMatch.ACCUMULATE,
                PasteEndMatch.MATCH,
                PasteEndMatch.ACCUMULATE
            ),
            matches
        )
    }
}