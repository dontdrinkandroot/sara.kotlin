package net.dontdrinkandroot.sara.systemprompt

import net.dontdrinkandroot.sara.systemprompt.sections.AboutSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.sections.ModesSystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.sections.SensitiveDataPolicySystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.sections.WebToolUsageSystemPromptProvider

class SaraSystemPromptProvider(
    webFetchEnabled: Boolean,
    webSearchEnabled: Boolean,
    exaEnabled: Boolean,
) : ChainedSystemPromptProvider(
    listOf(
        AboutSystemPromptProvider(),
        WebToolUsageSystemPromptProvider(webFetchEnabled, webSearchEnabled, exaEnabled),
        SensitiveDataPolicySystemPromptProvider(),
        ModesSystemPromptProvider(),
    ),
    separator = "\n\n"
)
