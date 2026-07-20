package net.dontdrinkandroot.sara.customizations

import kotlinx.serialization.Serializable

@Serializable
data class CustomizationEntry(
    val id: Int,
    val entry: String,
)

@Serializable
data class CustomizationsFile(
    val nextId: Int = 1,
    val installedPackages: List<CustomizationEntry> = emptyList(),
    val removedPackages: List<CustomizationEntry> = emptyList(),
    val configurationFiles: List<CustomizationEntry> = emptyList(),
    val services: List<CustomizationEntry> = emptyList(),
    val usersAndGroups: List<CustomizationEntry> = emptyList(),
    val scheduledTasks: List<CustomizationEntry> = emptyList(),
    val other: List<CustomizationEntry> = emptyList(),
)

fun CustomizationsFile.entriesOf(section: CustomizationSection): List<CustomizationEntry> =
    when (section) {
        CustomizationSection.INSTALLED_PACKAGES -> installedPackages
        CustomizationSection.REMOVED_PACKAGES -> removedPackages
        CustomizationSection.CONFIGURATION_FILES -> configurationFiles
        CustomizationSection.SERVICES -> services
        CustomizationSection.USERS_AND_GROUPS -> usersAndGroups
        CustomizationSection.SCHEDULED_TASKS -> scheduledTasks
        CustomizationSection.OTHER -> other
    }

fun CustomizationsFile.withEntries(
    section: CustomizationSection,
    entries: List<CustomizationEntry>,
): CustomizationsFile =
    when (section) {
        CustomizationSection.INSTALLED_PACKAGES -> copy(installedPackages = entries)
        CustomizationSection.REMOVED_PACKAGES -> copy(removedPackages = entries)
        CustomizationSection.CONFIGURATION_FILES -> copy(configurationFiles = entries)
        CustomizationSection.SERVICES -> copy(services = entries)
        CustomizationSection.USERS_AND_GROUPS -> copy(usersAndGroups = entries)
        CustomizationSection.SCHEDULED_TASKS -> copy(scheduledTasks = entries)
        CustomizationSection.OTHER -> copy(other = entries)
    }

fun CustomizationsFile.allEntries(): List<Pair<CustomizationSection, CustomizationEntry>> =
    CustomizationSection.entries.flatMap { section -> entriesOf(section).map { section to it } }

/**
 * Renders the customizations in the compact markdown style used for prompt injection and tool
 * results: `### <Heading>` sections (empty ones omitted) with `- [<id>] <entry>` bullet lines.
 */
fun CustomizationsFile.render(): String =
    CustomizationSection.entries
        .mapNotNull { section ->
            val entries = entriesOf(section)
            if (entries.isEmpty()) null
            else buildString {
                append("### ${section.heading}\n")
                entries.forEach { append("- [${it.id}] ${it.entry}\n") }
            }.trimEnd()
        }
        .joinToString("\n\n")
