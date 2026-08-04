package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.dontdrinkandroot.sara.configuration.ConfigurationError
import net.dontdrinkandroot.sara.configuration.loadConfiguration
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore
import net.dontdrinkandroot.sara.logger.ConsoleLogger
import net.dontdrinkandroot.sara.logger.LogLevel
import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SaraSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.StaticSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SystemCustomizationsProvider
import net.dontdrinkandroot.sara.systemprompt.systeminformation.SystemInformationSystemPromptProvider
import net.dontdrinkandroot.sara.tool.*
import kotlin.system.exitProcess

fun main(args: Array<String>) {

    val configuration = try {
        loadConfiguration(args)
    } catch (e: ConfigurationError) {
        println(e.message)
        exitProcess(1)
    }

    val terminal = Terminal()
    val logger = ConsoleLogger(terminal, if (configuration.verbose) LogLevel.DEBUG else LogLevel.INFO)

    SignalInterruptSource.install()

    logger.debug("Config loaded")
    logger.debug("searxngUrl=${configuration.searxngUrl}, exaApiKey=${configuration.exaApiKey != null}, verbose=${configuration.verbose}, braveMode=${configuration.braveMode}")
    logger.debug("model=${configuration.model}")
    logger.debug("systemPromptLength=${configuration.systemPrompt?.length}")

    val toolRegistry = ToolRegistry()
    toolRegistry.register(ExecCommandTool())
    toolRegistry.register(ReadFileTool())
    toolRegistry.register(WriteFileTool())
    val customizationsStore = SystemCustomizationsStore()
    toolRegistry.register(AddCustomizationTool(customizationsStore))
    toolRegistry.register(RemoveCustomizationTool(customizationsStore))
    toolRegistry.register(ReplaceCustomizationTool(customizationsStore))
    val webFetchClient: WebFetchClient? = if (configuration.exaApiKey == null) {
        WebFetchClient().also {
            toolRegistry.register(WebFetchTool(it))
        }
    } else {
        null
    }

    val searxngClient = configuration.searxngUrl?.let { url ->
        SearxngClient(url, configuration.searxngToken).also {
            toolRegistry.register(WebSearchTool(it))
        }
    }

    val exaClient = configuration.exaApiKey?.let { apiKey ->
        ExaClient(apiKey).also {
            toolRegistry.register(ExaSearchTool(it))
            toolRegistry.register(ExaContentsTool(it))
        }
    }

    val llmClient: LlmClient = DefaultLlmClient(
        baseUrl = configuration.baseUrl,
        apiKey = configuration.apiKey,
        siteUrl = "sara.dontdrinkandroot.net",
        siteTitle = "Sara"
    )

    val systemPromptProvider = ChainedSystemPromptProvider(
        listOf(
            SaraSystemPromptProvider(
                webFetchEnabled = configuration.exaApiKey == null,
                webSearchEnabled = configuration.searxngUrl != null,
                exaEnabled = configuration.exaApiKey != null,
            ),
            SystemCustomizationsProvider(customizationsStore),
            StaticSystemPromptProvider(configuration.systemPrompt),
            SystemInformationSystemPromptProvider()
        ),
        separator = "\n\n"
    )
    val sara = Sara(
        terminal = terminal,
        configuration = configuration,
        logger = logger,
        llmClient = llmClient,
        toolRegistry = toolRegistry,
        systemPromptProvider = systemPromptProvider,
    )
    try {
        runBlocking {
            sara.run()
        }
    } finally {
        llmClient.close()
        webFetchClient?.close()
        searxngClient?.close()
        exaClient?.close()
    }
}

