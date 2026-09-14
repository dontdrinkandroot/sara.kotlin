package net.dontdrinkandroot.sara

import com.github.ajalt.mordant.input.enterRawModeOrNull
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import net.dontdrinkandroot.sara.configuration.ConfigurationError
import net.dontdrinkandroot.sara.configuration.loadConfiguration
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore
import net.dontdrinkandroot.sara.editor.BracketedPasteMode
import net.dontdrinkandroot.sara.editor.MordantLineInput
import net.dontdrinkandroot.sara.editor.MordantRenderTarget
import net.dontdrinkandroot.sara.editor.PromptRenderer
import net.dontdrinkandroot.sara.editor.RawModeScopeReader
import net.dontdrinkandroot.sara.logger.ConsoleLogger
import net.dontdrinkandroot.sara.logger.LogLevel
import net.dontdrinkandroot.sara.session.FileSessionStore
import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.StaticSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.providers.InstructionsProvider
import net.dontdrinkandroot.sara.systemprompt.providers.SystemCustomizationsProvider
import net.dontdrinkandroot.sara.systemprompt.providers.systeminformation.SystemInformationProvider
import net.dontdrinkandroot.sara.tool.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.SIGINT
import platform.posix.getpid
import platform.posix.kill
import kotlin.system.exitProcess

/** Re-raises SIGINT so the installed handler fires after a raw-mode Ctrl+C key event. */
@OptIn(ExperimentalForeignApi::class)
private fun raiseProcessInterrupt() {
    kill(getpid(), SIGINT)
}

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

    // Multiline-aware prompt: raw-mode line editing with bracketed paste support. Raw
    // mode is entered per read, so Ctrl+C during LLM requests/tool runs still works via
    // the SIGINT handler. Ctrl+C at the prompt is re-raised via kill() so the handler
    // sees it too. Falls back to cooked input when raw mode is unavailable (piped IO).
    val bracketedPaste = BracketedPasteMode { terminal.rawPrint(it) }
    val renderTarget = MordantRenderTarget(terminal)
    val lineInput = MordantLineInput(
        readerFactory = {
            terminal.enterRawModeOrNull()?.let(::RawModeScopeReader)
        },
        rendererFactory = { PromptRenderer(renderTarget) },
        raiseInterrupt = { raiseProcessInterrupt() },
    )
    bracketedPaste.enable()

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
            InstructionsProvider(
                webFetchEnabled = configuration.exaApiKey == null,
                webSearchEnabled = configuration.searxngUrl != null,
                exaEnabled = configuration.exaApiKey != null,
            ),
            SystemCustomizationsProvider(customizationsStore),
            StaticSystemPromptProvider(configuration.systemPrompt),
            SystemInformationProvider()
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
        lineInput = lineInput,
        sessionStore = FileSessionStore(),
    )
    try {
        runBlocking {
            sara.run()
        }
    } finally {
        bracketedPaste.close()
        llmClient.close()
        webFetchClient?.close()
        searxngClient?.close()
        exaClient?.close()
    }
}
