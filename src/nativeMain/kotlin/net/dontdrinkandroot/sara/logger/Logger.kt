package net.dontdrinkandroot.sara.logger

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

interface Logger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String)
}

class ConsoleLogger(private val terminal: Terminal, private val level: LogLevel) : Logger {

    override fun debug(message: String) {
        if (level <= LogLevel.DEBUG) terminal.println(gray("[DEBUG] $message"))
    }

    override fun info(message: String) {
        if (level <= LogLevel.INFO) terminal.println(blue("[INFO] $message"))
    }

    override fun warn(message: String) {
        if (level <= LogLevel.WARN) terminal.println(yellow("[WARN] $message"))
    }

    override fun error(message: String) {
        if (level <= LogLevel.ERROR) terminal.println(red("[ERROR] $message"))
    }
}
