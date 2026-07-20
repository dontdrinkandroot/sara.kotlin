package net.dontdrinkandroot.sara.customizations

import kotlinx.io.files.Path
import kotlinx.serialization.json.Json
import net.dontdrinkandroot.sara.configuration.defaultConfigDir
import net.dontdrinkandroot.sara.extensions.exists
import net.dontdrinkandroot.sara.extensions.readString
import net.dontdrinkandroot.sara.extensions.writeWholeFile
import platform.posix.mkdir

/**
 * Loads and mutates `~/.config/sara/system-customizations.json`, the curated record of how this
 * system deviates from a default installation. Entries are addressed by stable integer IDs.
 */
class SystemCustomizationsStore(
    private val configDir: Path = defaultConfigDir(),
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val filePath: Path
        get() = Path("${configDir.toString().removeSuffix("/")}/$FILE_NAME")

    fun load(): CustomizationsFile {
        val parsed = runCatching {
            if (filePath.exists()) json.decodeFromString<CustomizationsFile>(filePath.readString()) else null
        }.getOrNull() ?: CustomizationsFile()

        val (repaired, changed) = repairIds(parsed)
        if (changed) save(repaired)
        return repaired
    }

    fun save(file: CustomizationsFile) {
        createDirectories(configDir.toString())
        writeWholeFile(filePath.toString(), json.encodeToString(CustomizationsFile.serializer(), file))
    }

    fun render(file: CustomizationsFile): String = file.render()

    fun add(section: CustomizationSection, entry: String): AddResult {
        val file = load()
        val trimmed = entry.trim()
        val existing = file.entriesOf(section).find { it.entry.trim().equals(trimmed, ignoreCase = true) }
        if (existing != null) return AddResult.AlreadyPresent(existing, file)

        val newEntry = CustomizationEntry(id = file.nextId, entry = trimmed)
        val updated = file.withEntries(section, file.entriesOf(section) + newEntry).copy(nextId = file.nextId + 1)
        save(updated)
        return AddResult.Added(newEntry, updated)
    }

    fun remove(id: Int): RemoveResult {
        val file = load()
        val match = file.allEntries().find { it.second.id == id } ?: return RemoveResult.NotFound(file)
        val (section, entry) = match
        val updated = file.withEntries(section, file.entriesOf(section).filterNot { it.id == id })
        save(updated)
        return RemoveResult.Removed(section, entry, updated)
    }

    fun replace(id: Int, newEntry: String): ReplaceResult {
        val file = load()
        val match = file.allEntries().find { it.second.id == id } ?: return ReplaceResult.NotFound(file)
        val (section, entry) = match
        val trimmed = newEntry.trim()

        val duplicate = file.entriesOf(section)
            .find { it.id != id && it.entry.trim().equals(trimmed, ignoreCase = true) }
        if (duplicate != null) return ReplaceResult.Duplicate(duplicate, file)

        val updated =
            file.withEntries(section, file.entriesOf(section).map { if (it.id == id) it.copy(entry = trimmed) else it })
        save(updated)
        return ReplaceResult.Replaced(section, entry.copy(entry = trimmed), updated)
    }

    /**
     * Assigns fresh IDs to entries with invalid (<= 0) or duplicate IDs and fixes `nextId`.
     * Returns the repaired file and whether any change was necessary.
     *
     * [nextId] is the lowest integer guaranteed to be unused after the entries processed so
     * far; it is advanced past any value already in [usedIds] just before it is handed out,
     * not after — so consecutive bad entries get strictly increasing IDs without further
     * bookkeeping. After all sections are processed, [nextId] is also bumped past the
     * maximum observed ID so the file's `nextId` field is always strictly greater than any
     * in-use ID.
     */
    private fun repairIds(file: CustomizationsFile): Pair<CustomizationsFile, Boolean> {
        val usedIds = mutableSetOf<Int>()
        var nextId = maxOf(file.nextId, 1)
        var changed = false

        var repaired = file
        for (section in CustomizationSection.entries) {
            val entries = repaired.entriesOf(section).map { entry ->
                if (entry.id > 0 && usedIds.add(entry.id)) {
                    entry
                } else {
                    changed = true
                    while (nextId in usedIds) nextId++
                    usedIds.add(nextId)
                    entry.copy(id = nextId)
                }
            }
            repaired = repaired.withEntries(section, entries)
        }

        val maxId = usedIds.maxOrNull() ?: 0
        if (repaired.nextId <= maxId) {
            repaired = repaired.copy(nextId = maxId + 1)
            changed = true
        }
        return repaired to changed
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

    sealed class AddResult {
        data class Added(val entry: CustomizationEntry, val file: CustomizationsFile) : AddResult()
        data class AlreadyPresent(val entry: CustomizationEntry, val file: CustomizationsFile) : AddResult()
    }

    sealed class RemoveResult {
        data class Removed(
            val section: CustomizationSection,
            val entry: CustomizationEntry,
            val file: CustomizationsFile,
        ) : RemoveResult()

        data class NotFound(val file: CustomizationsFile) : RemoveResult()
    }

    sealed class ReplaceResult {
        data class Replaced(
            val section: CustomizationSection,
            val entry: CustomizationEntry,
            val file: CustomizationsFile,
        ) : ReplaceResult()

        data class Duplicate(val existing: CustomizationEntry, val file: CustomizationsFile) : ReplaceResult()
        data class NotFound(val file: CustomizationsFile) : ReplaceResult()
    }

    companion object {
        const val FILE_NAME = "system-customizations.json"
    }
}
