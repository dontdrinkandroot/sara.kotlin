package net.dontdrinkandroot.sara.extensions

fun String.removeSurroundingQuotes(): String = when {
    startsWith('"') -> removeSurrounding("\"")
    startsWith('\'') -> removeSurrounding("'")
    else -> this
}

