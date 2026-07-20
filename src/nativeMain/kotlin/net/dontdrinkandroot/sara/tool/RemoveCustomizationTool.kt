package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore

/**
 * Removes a recorded system customization by its ID (e.g. when a change has been reverted).
 * Returns the freshly rendered state.
 */
class RemoveCustomizationTool(
    private val store: SystemCustomizationsStore,
) : ToolExecutor {

    override val name: String = "remove_customization"
    override val description: String =
        "Remove a recorded system customization by its ID, e.g. when the change has been reverted. " +
                "IDs are shown in the system customizations section of the system prompt."
    override val isSafe: Boolean = true
    override val availableInPlanMode: Boolean = false

    override fun getFunctionDescription(): FunctionDescription = FunctionDescription(
        name = name,
        description = description,
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("id", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("ID of the customization entry to remove"))
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("id"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val id = arguments["id"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("Missing or invalid required parameter: id (integer)")

        return try {
            when (val result = store.remove(id)) {
                is SystemCustomizationsStore.RemoveResult.Removed ->
                    ToolResult.Success(
                        "Removed entry [${result.entry.id}] from '${result.section.heading}': " +
                                result.entry.entry + currentStateSection(store, result.file)
                    )

                is SystemCustomizationsStore.RemoveResult.NotFound ->
                    ToolResult.Error(
                        "No customization entry with id $id" + currentStateSection(store, result.file)
                    )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to remove customization: ${e.message}")
        }
    }
}
