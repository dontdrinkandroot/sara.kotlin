@file:OptIn(ExperimentalForeignApi::class)

package net.dontdrinkandroot.sara.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.FunctionCall
import net.dontdrinkandroot.sara.Message
import net.dontdrinkandroot.sara.Mode
import net.dontdrinkandroot.sara.ToolCall
import net.dontdrinkandroot.sara.extensions.exists
import platform.posix.getenv
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.mkdir
import platform.posix.system
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileSessionStoreTest {

    @OptIn(ExperimentalForeignApi::class)
    private fun createTempDir(prefix: String): Path {
        val tmp = getenv("TMPDIR")?.toKString()?.takeIf(String::isNotEmpty) ?: "/tmp"
        var counter = 0
        while (true) {
            val candidate = Path("$tmp/$prefix-${counter++}")
            if (!candidate.exists()) {
                check(mkdir(candidate.toString(), 0x1FFu) == 0) { "mkdir failed for $candidate" }
                return candidate
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun writeText(path: Path, content: String) {
        val file = fopen(path.toString(), "w") ?: error("fopen failed for $path")
        try {
            val bytes = content.encodeToByteArray()
            bytes.usePinned { pinned ->
                fwrite(pinned.addressOf(0), 1.convert(), bytes.size.convert(), file)
            }
        } finally {
            fclose(file)
        }
    }

    private fun cleanup(path: Path) {
        system("rm -rf '${path.toString().replace("'", "'\\''")}'")
    }

    private fun filePath(dir: Path) = Path("$dir/${FileSessionStore.FILE_NAME}")

    @Test
    fun testLoadMissingFileReturnsNoSession() {
        val dir = createTempDir("sara-session-missing")
        try {
            assertEquals(SessionLoad.NoSession, FileSessionStore(dir).load())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testSaveAndLoadRoundtrip() {
        val dir = createTempDir("sara-session-roundtrip")
        try {
            val messages = listOf(
                Message(role = "system", content = "You are SARA."),
                Message(role = "user", content = "Hello"),
                Message(role = "assistant", content = "Hi!"),
            )
            val store = FileSessionStore(dir)
            store.save(messages, Mode.PLAN)

            val loaded = store.load()
            assertTrue(loaded is SessionLoad.Found)
            assertEquals("PLAN", loaded.saved.mode)
            assertEquals(messages, loaded.saved.messages)
            assertTrue(loaded.saved.savedAt.isNotEmpty())
            assertEquals(SavedSession.CURRENT_VERSION, loaded.saved.version)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testSaveRoundtripsToolMessages() {
        val dir = createTempDir("sara-session-tools")
        try {
            val messages = listOf(
                Message(
                    role = "assistant",
                    content = null,
                    toolCalls = listOf(
                        ToolCall(
                            id = "call-1",
                            type = "function",
                            function = FunctionCall(name = "exec_command", arguments = "{\"command\":\"ls\"}")
                        )
                    ),
                ),
                Message(role = "tool", toolCallId = "call-1", name = "exec_command", content = "file.txt"),
            )
            val store = FileSessionStore(dir)
            store.save(messages, Mode.EXEC)

            val loaded = store.load()
            assertTrue(loaded is SessionLoad.Found)
            assertEquals(messages, loaded.saved.messages)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testCorruptFileIsQuarantinedToBackup() {
        val dir = createTempDir("sara-session-corrupt")
        try {
            writeText(filePath(dir), "{ not json !!!")
            val store = FileSessionStore(dir)

            assertEquals(SessionLoad.Corrupt, store.load())
            assertFalse(filePath(dir).exists(), "corrupt file should be renamed away")
            assertTrue(
                Path("$dir/${FileSessionStore.BACKUP_FILE_NAME}").exists(),
                "corrupt file should be preserved as session.json.bak"
            )
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testDeleteRemovesFile() {
        val dir = createTempDir("sara-session-delete")
        try {
            val store = FileSessionStore(dir)
            store.save(listOf(Message(role = "user", content = "hi")), Mode.EXEC)
            assertTrue(filePath(dir).exists())

            store.delete()
            assertFalse(filePath(dir).exists())
            assertEquals(SessionLoad.NoSession, store.load())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testDeleteWithoutFileIsNoOp() {
        val dir = createTempDir("sara-session-delete-missing")
        try {
            FileSessionStore(dir).delete()
            assertFalse(filePath(dir).exists())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testUnknownModeFallsBackToExec() {
        assertEquals(Mode.EXEC, savedModeOrDefault(null))
        assertEquals(Mode.EXEC, savedModeOrDefault("GARBAGE"))
        assertEquals(Mode.PLAN, savedModeOrDefault("PLAN"))
    }
}
