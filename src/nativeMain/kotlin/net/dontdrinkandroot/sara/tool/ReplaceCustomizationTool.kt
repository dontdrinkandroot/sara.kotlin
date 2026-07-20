package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore

/**
 * Atomically replaces the text of a recorded system customization (keeping its ID).
 * Returns the freshly rendered state.
 */
class ReplaceCustomizationTool(
    private val store: SystemCustomizationsStore,
) : ToolExecutor {

    override val name: String = "replace_customization"
    override val description: String =
        "Replace the text of a recorded system customization, addressed by its ID (the ID is kept). " +
                "Use this to update an entry instead of removing and re-adding it."
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
                    put("description", JsonPrimitive("ID of the customization entry to replace"))
                })
                put("entry", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The new text for the entry"))
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("id"))
                add(JsonPrimitive("entry"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val id = arguments["id"]?.jsonPrimitive?.intOrNull
            ?: return ToolResult.Error("Missing or invalid required parameter: id (integer)")
        val entry = arguments["entry"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Missing required parameter: entry")

        return try {
            when (val result = store.replace(id, entry)) {
                is SystemCustomizationsStore.ReplaceResult.Replaced ->
                    ToolResult.Success(
                        "Replaced entry [${result.entry.id}] in '${result.section.heading}': " +
                                result.entry.entry + currentStateSection(store, result.file)
                    )

                is SystemCustomizationsStore.ReplaceResult.Duplicate ->
                    ToolResult.Error(
                        "An identical entry already exists with id ${result.existing.id}: " +
                                result.existing.entry + currentStateSection(store, result.file)
                    )

                is SystemCustomizationsStore.ReplaceResult.NotFound ->
                    ToolResult.Error(
                        "No customization entry with id $id" + currentStateSection(store, result.file)
                    )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to replace customization: ${e.message}")
        }
    }
}
