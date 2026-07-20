package net.dontdrinkandroot.sara.systemprompt

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.customizations.CustomizationSection
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore
import net.dontdrinkandroot.sara.extensions.exists
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.system
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
    fun testEntriesRenderedWithIdsWhenPresent() {
        val dir = createTempDir("sara-customizations-present")
        try {
            val store = SystemCustomizationsStore(dir)
            store.add(CustomizationSection.INSTALLED_PACKAGES, "vim")
            store.add(CustomizationSection.SERVICES, "docker — enabled")

            val provider = SystemCustomizationsProvider(store)
            val result = provider.provide()

            assertTrue(result.contains("## System Customizations"))
            assertTrue(result.contains("### Installed packages"))
            assertTrue(result.contains("- [1] vim"))
            assertTrue(result.contains("### Services"))
            assertTrue(result.contains("- [2] docker — enabled"))
            assertTrue(!result.contains(SystemCustomizationsProvider.MISSING_MARKER))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testLongContentsAreTruncated() {
        val dir = createTempDir("sara-customizations-trunc")
        try {
            val store = SystemCustomizationsStore(dir)
            val longEntry = "x".repeat(SystemCustomizationsProvider.MAX_CONTENT_LENGTH + 500)
            store.add(CustomizationSection.OTHER, longEntry)

            val provider = SystemCustomizationsProvider(store)
            val result = provider.provide()

            assertTrue(result.contains(SystemCustomizationsProvider.TRUNCATION_MARKER.trim()))
            assertTrue(!result.contains(longEntry))
            assertTrue(result.length <= SystemCustomizationsProvider.MAX_CONTENT_LENGTH + 4096)
        } finally {
            cleanup(dir)
        }
    }
}
