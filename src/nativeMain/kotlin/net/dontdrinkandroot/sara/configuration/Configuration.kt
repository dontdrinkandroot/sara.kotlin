@file:OptIn(ExperimentalForeignApi::class)

package net.dontdrinkandroot.sara.configuration

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.extensions.appendFileName
import net.dontdrinkandroot.sara.extensions.readStringIfExists
import platform.posix.getenv

sealed class ConfigurationError(message: String) : Exception(message) {

    class Multiple(errors: List<String>) : ConfigurationError(
        buildString {
            appendLine("Configuration errors detected:")
            errors.forEach { appendLine(" - $it") }
        }
    )
}

data class Configuration(
    val model: String,
    val apiKey: String,
    val baseUrl: String,
    val searxngUrl: String? = null,
    val searxngToken: String? = null,
    val verbose: Boolean = false,
    val systemPrompt: String?,
    val braveMode: Boolean = false,
)

/**
 * Loads configuration with precedence: CLI > Environment > .env file
 */
fun loadConfiguration(args: Array<String>): Configuration =
    parseCliArguments(args).let { cliArgs ->
        buildConfiguration(
            configDir = defaultConfigDir(),
            cliModel = cliArgs.model,
            verbose = cliArgs.verbose,
            systemPromptFile = cliArgs.systemPromptFile,
            braveMode = cliArgs.braveMode,
        )
    }

private data class CliArguments(
    val model: String?,
    val verbose: Boolean,
    val systemPromptFile: String?,
    val braveMode: Boolean,
)

private fun parseCliArguments(args: Array<String>): CliArguments {
    val parser = ArgParser("sara")

    val cliModel by parser.option(
        ArgType.String,
        fullName = "model",
        shortName = "m",
        description = "Model to use (overrides env and .env)"
    )

    val cliVerbose by parser.option(
        ArgType.Boolean,
        fullName = "verbose",
        shortName = "v",
        description = "Enable verbose output"
    ).default(false)

    val cliSystemPromptFile by parser.option(
        ArgType.String,
        fullName = "system-prompt-file",
        description = "Path to system prompt file (optional)"
    )

    val cliBraveMode by parser.option(
        ArgType.Boolean,
        fullName = "brave-mode",
        shortName = "b",
        description = "Enable brave mode (skip confirmation for tool execution)"
    ).default(false)

    parser.parse(args)

    return CliArguments(
        model = cliModel,
        verbose = cliVerbose,
        systemPromptFile = cliSystemPromptFile,
        braveMode = cliBraveMode
    )
}

private fun buildConfiguration(
    configDir: Path,
    cliModel: String?,
    verbose: Boolean,
    systemPromptFile: String?,
    braveMode: Boolean,
): Configuration {
    val env: Map<String, String> = readEnv(configDir)

    val errors = mutableListOf<String>()

    fun resolveRequired(
        envName: String,
        cliValue: String? = null,
        cliFlag: String? = null,
    ): String? =
        resolveConfigValue(envName, cliValue, env)
            ?: errors.addMissingConfigError(envName, cliFlag, configDir).let { null }

    val model = resolveRequired(envName = "SARA_MODEL", cliValue = cliModel, cliFlag = "--model")
    val apiKey = resolveRequired(envName = "SARA_API_KEY")
    val baseUrl = resolveRequired(envName = "SARA_BASE_URL")

    if (errors.isNotEmpty()) {
        throw ConfigurationError.Multiple(errors)
    }

    val searxngUrl = env["SARA_SEARXNG_URL"]?.takeIf(String::isNotBlank)
    val searxngToken = env["SARA_SEARXNG_TOKEN"]?.takeIf(String::isNotBlank)

    val systemPromptPath: Path = when (val cliPath = systemPromptFile) {
        null -> configDir.appendFileName("system-prompt.md")
        else -> Path(expandHome(cliPath))
    }
    val systemPrompt = loadSystemPrompt(systemPromptPath)

    return Configuration(
        model = requireNotNull(model),
        apiKey = requireNotNull(apiKey),
        baseUrl = requireNotNull(baseUrl),
        searxngUrl = searxngUrl,
        searxngToken = searxngToken,
        verbose = verbose,
        systemPrompt = systemPrompt,
        braveMode = braveMode,
    )
}

/**
 * Resolves a configuration value with precedence:
 * CLI > Merged environment (ENV > .env.local > .env)
 */
private fun resolveConfigValue(
    envName: String,
    cliValue: String?,
    mergedEnv: Map<String, String>,
): String? =
    cliValue?.takeIf(String::isNotBlank)
        ?: mergedEnv[envName]?.takeIf(String::isNotBlank)

/**
 * Adds a generic, consistently formatted error message for a missing configuration value.
 */
private fun MutableList<String>.addMissingConfigError(
    envName: String,
    cliFlag: String?,
    configDir: Path,
) {
    val cliPart = cliFlag?.let { "$it, " }.orEmpty()
    add("$envName is not defined. Provide via ${cliPart}env $envName, or in $configDir/.env")
}

fun defaultConfigDir(): Path =
    getenv("HOME")
        ?.toKString()
        ?.takeIf(String::isNotEmpty)
        ?.let { Path("$it/.config/sara") }
        ?: Path(".")

private fun loadSystemPrompt(path: Path): String? =
    path.readStringIfExists()?.takeIf(String::isNotBlank)

private fun expandHome(path: String): String {
    if (!path.startsWith("~")) return path
    val home = getenv("HOME")?.toKString().orEmpty()
    return home + path.removePrefix("~")
}
