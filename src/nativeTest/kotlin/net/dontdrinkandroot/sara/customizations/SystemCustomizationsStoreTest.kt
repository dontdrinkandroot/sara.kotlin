package net.dontdrinkandroot.sara.customizations

import kotlinx.cinterop.*
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.extensions.exists
import platform.posix.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SystemCustomizationsStoreTest {

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

    private fun filePath(dir: Path) = Path("$dir/${SystemCustomizationsStore.FILE_NAME}")

    @Test
    fun testLoadMissingFileReturnsEmpty() {
        val dir = createTempDir("sara-store-missing")
        try {
            val store = SystemCustomizationsStore(dir)
            val file = store.load()
            assertEquals(1, file.nextId)
            assertTrue(file.allEntries().isEmpty())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testLoadCorruptFileReturnsEmpty() {
        val dir = createTempDir("sara-store-corrupt")
        try {
            writeText(filePath(dir), "{ not json !!!")
            val store = SystemCustomizationsStore(dir)
            assertTrue(store.load().allEntries().isEmpty())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testAddAssignsSequentialIdsAndCreatesFile() {
        val dir = createTempDir("sara-store-add")
        try {
            val nested = Path("$dir/nested/config")
            val store = SystemCustomizationsStore(nested)

            val first = store.add(CustomizationSection.INSTALLED_PACKAGES, "ripgrep")
            val second = store.add(CustomizationSection.SERVICES, "docker — enabled")

            assertIs<SystemCustomizationsStore.AddResult.Added>(first)
            assertIs<SystemCustomizationsStore.AddResult.Added>(second)
            assertEquals(1, first.entry.id)
            assertEquals(2, second.entry.id)

            val loaded = SystemCustomizationsStore(nested).load()
            assertEquals(3, loaded.nextId)
            assertEquals(listOf(CustomizationEntry(1, "ripgrep")), loaded.installedPackages)
            assertEquals(listOf(CustomizationEntry(2, "docker — enabled")), loaded.services)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testAddIsIdempotentCaseInsensitive() {
        val dir = createTempDir("sara-store-dedupe")
        try {
            val store = SystemCustomizationsStore(dir)
            store.add(CustomizationSection.INSTALLED_PACKAGES, "ripgrep")
            val result = store.add(CustomizationSection.INSTALLED_PACKAGES, "  Ripgrep ")

            assertIs<SystemCustomizationsStore.AddResult.AlreadyPresent>(result)
            assertEquals(1, result.entry.id)
            assertEquals(1, store.load().installedPackages.size)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRemoveById() {
        val dir = createTempDir("sara-store-remove")
        try {
            val store = SystemCustomizationsStore(dir)
            store.add(CustomizationSection.INSTALLED_PACKAGES, "ripgrep")
            store.add(CustomizationSection.INSTALLED_PACKAGES, "fd")

            val removed = store.remove(1)
            assertIs<SystemCustomizationsStore.RemoveResult.Removed>(removed)
            assertEquals("ripgrep", removed.entry.entry)

            val loaded = store.load()
            assertEquals(listOf(CustomizationEntry(2, "fd")), loaded.installedPackages)

            assertIs<SystemCustomizationsStore.RemoveResult.NotFound>(store.remove(99))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testReplaceKeepsId() {
        val dir = createTempDir("sara-store-replace")
        try {
            val store = SystemCustomizationsStore(dir)
            store.add(CustomizationSection.SERVICES, "docker — disabled")

            val result = store.replace(1, "docker — enabled")
            assertIs<SystemCustomizationsStore.ReplaceResult.Replaced>(result)
            assertEquals(CustomizationEntry(1, "docker — enabled"), result.entry)
            assertEquals(
                listOf(CustomizationEntry(1, "docker — enabled")),
                store.load().services
            )

            assertIs<SystemCustomizationsStore.ReplaceResult.NotFound>(store.replace(42, "x"))
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testReplaceRejectsDuplicate() {
        val dir = createTempDir("sara-store-replace-dup")
        try {
            val store = SystemCustomizationsStore(dir)
            store.add(CustomizationSection.INSTALLED_PACKAGES, "ripgrep")
            store.add(CustomizationSection.INSTALLED_PACKAGES, "fd")

            val result = store.replace(2, "ripgrep")
            assertIs<SystemCustomizationsStore.ReplaceResult.Duplicate>(result)
            assertEquals(1, result.existing.id)
            assertEquals(
                listOf(CustomizationEntry(1, "ripgrep"), CustomizationEntry(2, "fd")),
                store.load().installedPackages
            )
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRepairAssignsIdsToIdlessEntries() {
        val dir = createTempDir("sara-store-repair")
        try {
            writeText(
                filePath(dir),
                """{"nextId": 1, "installedPackages": [{"id": 0, "entry": "vim"}, {"id": 5, "entry": "htop"}, {"id": 5, "entry": "dup"}]}"""
            )
            val loaded = SystemCustomizationsStore(dir).load()

            val ids = loaded.installedPackages.map { it.id }
            assertEquals(3, ids.distinct().size)
            assertTrue(ids.all { it > 0 })
            assertTrue(loaded.nextId > (ids.maxOrNull() ?: 0))
            // repair persisted
            assertEquals(loaded, SystemCustomizationsStore(dir).load())
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRepairClampsNonPositiveNextIdToOne() {
        val dir = createTempDir("sara-store-repair-nextid-zero")
        try {
            writeText(
                filePath(dir),
                """{"nextId": 0, "installedPackages": [{"id": 0, "entry": "vim"}]}"""
            )
            val loaded = SystemCustomizationsStore(dir).load()
            assertEquals(1, loaded.installedPackages.first().id)
            assertTrue(loaded.nextId > 1)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRepairBumpsNextIdPastMaxEntryId() {
        val dir = createTempDir("sara-store-repair-nextid-low")
        try {
            writeText(
                filePath(dir),
                """{"nextId": 2, "installedPackages": [{"id": 7, "entry": "vim"}]}"""
            )
            val loaded = SystemCustomizationsStore(dir).load()
            assertEquals(8, loaded.nextId)
            assertEquals(listOf(7), loaded.installedPackages.map { it.id })
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRepairToleratesMissingSections() {
        val dir = createTempDir("sara-store-repair-missing-sections")
        try {
            writeText(
                filePath(dir),
                """{"nextId": 3, "installedPackages": [{"id": 2, "entry": "vim"}]}"""
            )
            val loaded = SystemCustomizationsStore(dir).load()
            assertTrue(loaded.services.isEmpty())
            assertTrue(loaded.configurationFiles.isEmpty())
            assertEquals(3, loaded.nextId)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRenderGroupsBySectionWithIds() {
        val file = CustomizationsFile(
            nextId = 3,
            installedPackages = listOf(CustomizationEntry(1, "ripgrep")),
            services = listOf(CustomizationEntry(2, "docker — enabled")),
        )
        val rendered = file.render()
        assertEquals(
            "### Installed packages\n- [1] ripgrep\n\n### Services\n- [2] docker — enabled",
            rendered
        )
    }
}
