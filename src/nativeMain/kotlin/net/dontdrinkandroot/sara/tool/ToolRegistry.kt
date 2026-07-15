package net.dontdrinkandroot.sara.tool

import net.dontdrinkandroot.sara.Tool

/**
 * Registry for managing available tools.
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, ToolExecutor>()

    /**
     * Registers a tool in the registry.
     */
    fun register(tool: ToolExecutor) {
        tools[tool.name] = tool
    }

    /**
     * Gets a tool by name.
     */
    fun get(name: String): ToolExecutor? = tools[name]

    /**
     * Returns all registered tools.
     */
    fun getAll(): List<ToolExecutor> = tools.values.toList()

    /**
     * Returns tool schemas for the OpenRouter API.
     */
    fun getToolSchemas(): List<Tool> = tools.values.map { executor ->
        Tool(
            type = "function",
            function = executor.getFunctionDescription()
        )
    }
}
