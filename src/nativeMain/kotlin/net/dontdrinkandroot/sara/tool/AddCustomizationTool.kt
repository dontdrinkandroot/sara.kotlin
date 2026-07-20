package net.dontdrinkandroot.sara.tool

import kotlinx.serialization.json.*
import net.dontdrinkandroot.sara.FunctionDescription
import net.dontdrinkandroot.sara.customizations.CustomizationSection
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore

/**
 * Records a system customization (a deviation from a default installation) under a section.
 * Returns the assigned entry ID along with the freshly rendered state.
 */
class AddCustomizationTool(
    private val store: SystemCustomizationsStore,
) : ToolExecutor {

    override val name: String = "add_customization"
    override val description: String =
        "Record a system customization (installed/removed package, config file, service, user/group, " +
                "scheduled task, or other deviation from a default installation). Idempotent: adding an " +
                "existing entry returns its current ID."
    override val isSafe: Boolean = true
    override val availableInPlanMode: Boolean = false

    override fun getFunctionDescription(): FunctionDescription = FunctionDescription(
        name = name,
        description = description,
        parameters = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                put("section", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive("Category the customization belongs to")
                    )
                    put("enum", buildJsonArray {
                        CustomizationSection.entries.forEach { add(JsonPrimitive(it.key)) }
                    })
                })
                put("entry", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put(
                        "description",
                        JsonPrimitive(
                            "One concise, factual line: package name (version only if pinned), " +
                                    "config path + one-line description, service + desired state. " +
                                    "No timestamps, narration, or command transcripts."
                        )
                    )
                })
            })
            put("required", buildJsonArray {
                add(JsonPrimitive("section"))
                add(JsonPrimitive("entry"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject, verbose: Boolean): ToolResult {
        val sectionArg = arguments["section"]?.jsonPrimitive?.content
            ?: return ToolResult.Error("Missing required parameter: section")
        val section = CustomizationSection.fromKey(sectionArg)
            ?: return ToolResult.Error(
                "Unknown section: '$sectionArg'. Valid sections: " +
                        CustomizationSection.entries.joinToString(", ") { it.key }
            )
        val entry = arguments["entry"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("Missing required parameter: entry")

        return try {
            when (val result = store.add(section, entry)) {
                is SystemCustomizationsStore.AddResult.Added ->
                    ToolResult.Success(
                        "Added entry [${result.entry.id}] to '${section.heading}': ${result.entry.entry}" +
                                currentStateSection(store, result.file)
                    )

                is SystemCustomizationsStore.AddResult.AlreadyPresent ->
                    ToolResult.Success(
                        "Entry already present in '${section.heading}' with id ${result.entry.id}: " +
                                result.entry.entry + currentStateSection(store, result.file)
                    )
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to add customization: ${e.message}")
        }
    }
}
