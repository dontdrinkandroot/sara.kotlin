package net.dontdrinkandroot.sara.editor

/** The `ESC` character starting escape sequences. */
internal const val ESC_CHAR = '\u001b'

/**
 * Pure state machine that recognizes the bracketed paste start marker `ESC [ 2 0 0 ~`
 * one character at a time, in cooked mode or inside a bracketed paste.
 *
 * [PendingPasteMarker] is the lookahead state of characters already consumed but not yet
 * classified; [nextPasteMarkerState] returns the resulting action plus the next state.
 */

/** The result of feeding one character to the start-marker recognizer. */
internal sealed interface PasteMarkerStep {
    /** Not part of a start marker: emit [char] as regular text. */
    data class PassThrough(val char: Char) : PasteMarkerStep

    /** The character extends a possible start marker; [next] is the new lookahead state. */
    data class MaybeStart(val next: PendingPasteMarker) : PasteMarkerStep

    /** A previous partial marker turned out to be something else; [emitted] is its text. */
    data class Reject(val emitted: String) : PasteMarkerStep

    /** The full start marker was recognized. */
    data object Start : PasteMarkerStep
}

internal fun nextPasteMarkerState(c: Char, pending: PendingPasteMarker): PasteMarkerStep = when (pending) {
    PendingPasteMarker.NONE ->
        if (c == ESC_CHAR) PasteMarkerStep.MaybeStart(PendingPasteMarker.ESC) else PasteMarkerStep.PassThrough(c)

    PendingPasteMarker.ESC ->
        when (c) {
            '[' -> PasteMarkerStep.MaybeStart(PendingPasteMarker.ESC_BRACKET)
            else -> PasteMarkerStep.Reject("${pending.consumedText()}$c")
        }

    PendingPasteMarker.ESC_BRACKET ->
        when (c) {
            '2' -> PasteMarkerStep.MaybeStart(PendingPasteMarker.ESC_BRACKET_2)
            else -> PasteMarkerStep.Reject("${pending.consumedText()}$c")
        }

    PendingPasteMarker.ESC_BRACKET_2 ->
        when (c) {
            '0' -> PasteMarkerStep.MaybeStart(PendingPasteMarker.ESC_BRACKET_20)
            else -> PasteMarkerStep.Reject("${pending.consumedText()}$c")
        }

    PendingPasteMarker.ESC_BRACKET_20 ->
        when (c) {
            '0' -> PasteMarkerStep.MaybeStart(PendingPasteMarker.ESC_BRACKET_200)
            else -> PasteMarkerStep.Reject("${pending.consumedText()}$c")
        }

    PendingPasteMarker.ESC_BRACKET_200 ->
        when (c) {
            '~' -> PasteMarkerStep.Start
            else -> PasteMarkerStep.Reject("${pending.consumedText()}$c")
        }
}

/**
 * Pure matching of the paste-end marker `ESC [ 2 0 1 ~` against the pending suffix.
 *
 * The [pending] prefix was already consumed from the stream and not yet emitted; [c] is
 * the next character. Returns [PasteEndMatch.MATCH] when the character completes
 * `ESC[201~`, [PasteEndMatch.ACCUMULATE] while the prefix still matches, and
 * [PasteEndMatch.MISMATCH] on the first mismatching character.
 */
internal fun matchPasteEndMarker(c: Char, pending: PendingPasteMarker): PasteEndMatch = when (pending) {
    PendingPasteMarker.NONE -> PasteEndMatch.ACCUMULATE
    PendingPasteMarker.ESC -> if (c == '[') PasteEndMatch.ACCUMULATE else PasteEndMatch.MISMATCH
    PendingPasteMarker.ESC_BRACKET -> if (c == '2') PasteEndMatch.ACCUMULATE else PasteEndMatch.MISMATCH
    PendingPasteMarker.ESC_BRACKET_2 -> if (c == '0') PasteEndMatch.ACCUMULATE else PasteEndMatch.MISMATCH
    PendingPasteMarker.ESC_BRACKET_20 -> if (c == '1') PasteEndMatch.MATCH else PasteEndMatch.MISMATCH
    else -> PasteEndMatch.MISMATCH
}

internal enum class PasteEndMatch { ACCUMULATE, MATCH, MISMATCH }