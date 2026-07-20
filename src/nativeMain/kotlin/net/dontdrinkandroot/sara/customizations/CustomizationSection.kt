package net.dontdrinkandroot.sara.customizations

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Fixed set of categories under which system customizations are recorded. [key] matches the
 * top-level keys of `system-customizations.json` (and the `section` argument of the
 * customization tools).
 */
@Serializable
enum class CustomizationSection(val key: String, val heading: String) {
    @SerialName("installedPackages")
    INSTALLED_PACKAGES("installedPackages", "Installed packages"),

    @SerialName("removedPackages")
    REMOVED_PACKAGES("removedPackages", "Removed/purged packages"),

    @SerialName("configurationFiles")
    CONFIGURATION_FILES("configurationFiles", "Configuration files"),

    @SerialName("services")
    SERVICES("services", "Services"),

    @SerialName("usersAndGroups")
    USERS_AND_GROUPS("usersAndGroups", "Users and groups"),

    @SerialName("scheduledTasks")
    SCHEDULED_TASKS("scheduledTasks", "Scheduled tasks"),

    @SerialName("other")
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String): CustomizationSection? =
            entries.find { it.key.equals(key, ignoreCase = true) }
    }
}
