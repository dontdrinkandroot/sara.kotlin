package net.dontdrinkandroot.sara.editor

/** The `ESC` character starting escape sequences. */
internal enum class PendingPasteMarker {
    NONE,
    ESC,
    ESC_BRACKET,
    ESC_BRACKET_2,
    ESC_BRACKET_20,
    ESC_BRACKET_200;

    /**
     * The characters already consumed and held back by this state, e.g. [ESC_BRACKET_2]
     * holds `ESC[2`.
     */
    fun consumedText(): String? = when (this) {
        NONE -> null
        ESC -> "${ESC_CHAR}"
        ESC_BRACKET -> "${ESC_CHAR}["
        ESC_BRACKET_2 -> "${ESC_CHAR}[2"
        ESC_BRACKET_20 -> "${ESC_CHAR}[20"
        ESC_BRACKET_200 -> "${ESC_CHAR}[200"
    }
}