package net.dontdrinkandroot.sara

import net.dontdrinkandroot.sara.logger.Logger

object NoOpLogger : Logger {
    override fun debug(message: String) {}
    override fun info(message: String) {}
    override fun warn(message: String) {}
    override fun error(message: String) {}
}
