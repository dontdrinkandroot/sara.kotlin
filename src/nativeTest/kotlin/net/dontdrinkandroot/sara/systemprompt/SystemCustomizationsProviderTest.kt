package net.dontdrinkandroot.sara.systemprompt

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.extensions.exists
import platform.posix.*
import kotlin.test.Test
import kotlin.test.assertTrue

class SystemCustomizationsProviderTest {

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

    @Test
    fun testInstructionsPresentWhenFileMissing() {
        val dir = createTempDir("sara-customizations-missing")
        try {
            val provider = SystemCustomizationsProvider(configDir = dir)
            val result = provider.provide()

            assertTrue(result.contains("## System Customizations"))
            assertTrue(result.contains(SystemCustomizationsProvider.MISSING_MARKER))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testFileContentsInjectedWhenPresent() {
        val dir = createTempDir("sara-customizations-present")
        try {
            val file = Path("${dir.toString()}/${SystemCustomizationsProvider.CUSTOMIZATIONS_FILE_NAME}")
            writeText(file, "### Installed packages\n- vim\n")
            val provider = SystemCustomizationsProvider(configDir = dir)
            val result = provider.provide()

            assertTrue(result.contains("## System Customizations"))
            assertTrue(result.contains("### Installed packages"))
            assertTrue(result.contains("- vim"))
            assertTrue(!result.contains(SystemCustomizationsProvider.MISSING_MARKER))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testLongContentsAreTruncated() {
        val dir = createTempDir("sara-customizations-trunc")
        try {
            val file = Path("${dir.toString()}/${SystemCustomizationsProvider.CUSTOMIZATIONS_FILE_NAME}")
            val longBody = "x".repeat(SystemCustomizationsProvider.MAX_CONTENT_LENGTH + 500)
            writeText(file, longBody)
            val provider = SystemCustomizationsProvider(configDir = dir)
            val result = provider.provide()

            assertTrue(result.contains(SystemCustomizationsProvider.TRUNCATION_MARKER.trim()))
            assertTrue(!result.contains(longBody))
            val expectedTail = "x".repeat(SystemCustomizationsProvider.MAX_CONTENT_LENGTH) +
                    SystemCustomizationsProvider.TRUNCATION_MARKER
            assertTrue(result.endsWith(expectedTail))
        } finally {
            cleanup(dir)
        }
    }
}
