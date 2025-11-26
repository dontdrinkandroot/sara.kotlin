package net.dontdrinkandroot.sara.systemprompt

open class StaticSystemPromptProvider(val prompt: String?) : SystemPromptProvider {
    override fun provide(): String? = prompt
}
