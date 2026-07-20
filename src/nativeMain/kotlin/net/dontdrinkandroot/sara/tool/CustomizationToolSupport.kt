package net.dontdrinkandroot.sara.tool

import net.dontdrinkandroot.sara.customizations.CustomizationsFile
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore

/**
 * Shared result helpers for the customization tools: every mutation returns the freshly
 * rendered state so the agent always has current IDs.
 */
internal fun currentStateSection(store: SystemCustomizationsStore, file: CustomizationsFile): String {
    val rendered = store.render(file)
    return when {
        rendered.isBlank() -> "\n\nCurrent system customizations: (none recorded yet)"
        else -> "\n\nCurrent system customizations:\n$rendered"
    }
}
