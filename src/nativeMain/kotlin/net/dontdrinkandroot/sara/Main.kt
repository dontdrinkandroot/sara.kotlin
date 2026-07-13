package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.dontdrinkandroot.sara.configuration.ConfigurationError
import net.dontdrinkandroot.sara.configuration.loadConfiguration
import net.dontdrinkandroot.sara.logger.ConsoleLogger
import net.dontdrinkandroot.sara.logger.LogLevel
import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SaraSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.StaticSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.SystemInformationSystemPromptProvider
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

    logger.debug("Config loaded")
    logger.debug("searxngUrl=${configuration.searxngUrl}, verbose=${configuration.verbose}, braveMode=${configuration.braveMode}")
    logger.debug("model=${configuration.model}")
    logger.debug("systemPromptLength=${configuration.systemPrompt?.length}")

    val toolRegistry = ToolRegistry()
    toolRegistry.register(ExecCommandTool())
    toolRegistry.register(ReadFileTool())
    toolRegistry.register(WriteFileTool())
    val webFetchClient = WebFetchClient()
    toolRegistry.register(WebFetchTool(webFetchClient))

    val searxngClient = configuration.searxngUrl?.let { url ->
        SearxngClient(url, configuration.searxngToken).also {
            toolRegistry.register(WebSearchTool(it))
        }
    }

    val llmClient = LlmClient(
        baseUrl = configuration.baseUrl,
        apiKey = configuration.apiKey,
        siteUrl = "sara.dontdrinkandroot.net",
        siteTitle = "Sara"
    )

    val systemPromptProvider = ChainedSystemPromptProvider(
        listOf(
            SaraSystemPromptProvider(),
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
        webFetchClient.close()
        searxngClient?.close()
    }
}

