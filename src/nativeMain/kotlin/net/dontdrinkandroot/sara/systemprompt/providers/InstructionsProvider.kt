package net.dontdrinkandroot.sara.systemprompt.providers

import net.dontdrinkandroot.sara.systemprompt.ChainedSystemPromptProvider

class InstructionsProvider(
    webFetchEnabled: Boolean,
    webSearchEnabled: Boolean,
    exaEnabled: Boolean,
) : ChainedSystemPromptProvider(
    listOf(
        AboutProvider(),
        WebToolUsageProvider(webFetchEnabled, webSearchEnabled, exaEnabled),
        SensitiveDataPolicyProvider(),
        ModesProvider(),
    ),
    separator = "\n\n"
)
