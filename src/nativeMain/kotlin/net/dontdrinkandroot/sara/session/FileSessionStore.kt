package net.dontdrinkandroot.sara.session

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import net.dontdrinkandroot.sara.Message
import net.dontdrinkandroot.sara.Mode
import net.dontdrinkandroot.sara.configuration.defaultConfigDir
import net.dontdrinkandroot.sara.extensions.exists
import net.dontdrinkandroot.sara.extensions.readString
import net.dontdrinkandroot.sara.extensions.writeWholeFile
import platform.posix.mkdir
import platform.posix.rename

/**
 * File-backed [SessionStore] persisting the session to `<configDir>/session.json`
 * (pretty-printed JSON, rewritten after every conversation change).
 *
 * A corrupt file is renamed to `session.json.bak` (preserved for manual inspection,
 * overwriting a previous backup) and treated as absent.
 */
class FileSessionStore(
    private val configDir: Path = defaultConfigDir(),
) : SessionStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val filePath: Path
        get() = Path("${configDir.toString().removeSuffix("/")}/$FILE_NAME")

    private val backupPath: Path
        get() = Path("${configDir.toString().removeSuffix("/")}/$BACKUP_FILE_NAME")

    override fun load(): SessionLoad {
        if (!filePath.exists()) return SessionLoad.NoSession

        return try {
            SessionLoad.Found(json.decodeFromString(SavedSession.serializer(), filePath.readString()))
        } catch (e: Exception) {
            quarantineCorruptFile()
            SessionLoad.Corrupt
        }
    }

    override fun save(messages: List<Message>, mode: Mode) {
        createDirectories(configDir.toString())
        val session = SavedSession(
            savedAt = currentTimestamp(),
            mode = mode.name,
            messages = messages,
        )
        writeWholeFile(filePath.toString(), json.encodeToString(SavedSession.serializer(), session))
    }

    override fun delete() {
        if (filePath.exists()) {
            SystemFileSystem.delete(filePath)
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun quarantineCorruptFile() {
        try {
            rename(filePath.toString(), backupPath.toString())
        } catch (e: Exception) {
            // Best effort: leave the corrupt file in place if the rename fails.
        }
    }

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    private fun createDirectories(path: String) {
        var current = ""
        for (part in path.split('/')) {
            if (part.isEmpty() || part == ".") continue
            current += "/$part"
            mkdir(current, 0x1EDu) // 0755; fails silently if it already exists
        }
    }

    companion object {
        const val FILE_NAME = "session.json"
        const val BACKUP_FILE_NAME = "session.json.bak"

        /**
         * Current local time in ISO 8601 (via `date -Is`); empty string if unavailable.
         */
        internal fun currentTimestamp(): String =
            executeCommandSafeDate() ?: ""
    }
}

private fun executeCommandSafeDate(): String? = try {
    net.dontdrinkandroot.sara.tool.executeCommand("date -Is").trim().takeIf { it.isNotEmpty() }
} catch (e: Exception) {
    null
}
