---
apply: always
---

## About

* This is the codebase of SARA (System Action & Response Agent), an LLM Agent Interface for the CLI that helps the user
  use their Linux/Unix operating system.
* It is written in Kotlin/Native 2.2.0
* It uses ktor 3.3.1 to handle the API requests against an OpenAI compatible API
* It uses mordant 3.0.2 for styling the output and formatting the Markdown output

## Coding Rules

* We adhere to Clean Code and SOLID principles.
* Remember that Clean Code implies using speaking variable and function names to avoid unnecessary comments.
* As we are SOLID, we keep an eye on testability as it will indicate a good separation.
* We love to use Kotlin sugar if the code remains readable or even helps it.

## General Instructions

* We update this general rules file after performing a task to document new features or other relevant project changes
  that could be useful for an LLM to get a quick overview.

## Environment

* The configuration is stored in `~/.config/sara/`
    * There is a `.env` file in the directory that provides the `API_KEY` variable, that contains the bearer to interact
      with the LLM Provider, a `MODEL` variable that specifies which model to use, and a required `BASE_URL` variable
      that points to the OpenAI-compatible API base (without the trailing path).
    * There is an optional `system-prompt.md` file that contains a System Prompt which is prepended to each session.

## Build

* The result is a binary called `sara.kexe` that can be built with `gradle build`.
* We always run `gradle build` after performing a task to see if the build succeeds and the tests are green.

## Testing

* We use the `kotlin-test` framework for testing.

## Features

### Configuration loading and precedence

We load configuration from the user config directory `~/.config/sara/`.

- Primary config file: `~/.config/sara/.env`
- Optional system prompt file: `~/.config/sara/system-prompt.md`

Variables and precedence for model selection, API key, and API base URL:

- Variables: `SARA_MODEL`, `SARA_API_KEY`, and `SARA_BASE_URL`.
- Sources and precedence (later overrides earlier):
    1) `.env` file values
    2) Real environment variables `SARA_MODEL`, `SARA_API_KEY`, and `SARA_BASE_URL`
    3) Command line options (CLI)
- Failure behavior: If after applying precedence any of `SARA_MODEL`, `SARA_API_KEY`, or `SARA_BASE_URL` is undefined,
  SARA fails fast
  with a clear error message.

Provider base URL examples for `.env`:

```
# OpenRouter
SARA_BASE_URL=https://openrouter.ai/api/v1
SARA_MODEL=openai/gpt-4o
SARA_API_KEY=sk-or-...

# OpenAI
# SARA_BASE_URL=https://api.openai.com/v1
# SARA_MODEL=gpt-4o
# SARA_API_KEY=sk-...

# Mammouth.ai (example; verify with provider docs)
# SARA_BASE_URL=https://api.mammouth.ai/v1
# SARA_MODEL=meta-llama/llama-3.1-70b-instruct
# SARA_API_KEY=mm-...
```

System prompt handling:

- If the `system-prompt.md` file exists, its content is read and prepended as a system message (see also System Prompt
  Injection below). The file is optional; absence is not an error.

Other CLI options:

- All other tunables can be provided via CLI options and have sensible defaults as documented below. CLI-provided values
  override any defaults.

### System Prompt Injection

If `~/.config/sara/system-prompt.md` exists, its content is prepended to each session as a system message.

In addition, SARA automatically injects useful system context at startup via built‑in providers, in this order:

- System hostname
- Current OS user
- Disk usage summary (from `df -h`)
- Top processes by CPU and memory (from `ps`)

If present, the user’s `system-prompt.md` is appended after these automatic sections. If the file is missing or empty,
only the automatic sections are used. If any automatic section cannot be collected (e.g., command unavailable), it is
skipped gracefully.

### CLI Interaction

- Interactive REPL: reads user input from standard input (System.in).
- Session ends when the user submits an empty line or EOF (Ctrl+D) is encountered.
- Verbose mode (-v/--verbose) prints a short REPL start hint.

### Web Search

Run with `-s` or `--search` to enable web search for the model. This is an OpenRouter-specific feature and will be
ignored by providers that do not support it.

### Verbose Debug Logging

Run with `-v` or `--verbose` to enable debug output (e.g., config summary, system prompt length, and detailed tool call
information).

### Progress indicator

- While waiting for the LLM response a transient status line is shown using Mordant's Terminal. The indicator is scoped
  to the model request and stops automatically before any prompts or tool execution. No manual pause/resume is needed.

### Markdown Rendering

- LLM responses are rendered as Markdown in the terminal using Mordant 3.0.2.

### Tool Calling Support

- The assistant can request tools; SARA exposes an `exec_command` tool to run local commands.
- Arguments: `command` (string) and optional `args` (string array).
- By default SARA asks for explicit user confirmation before executing any command and returns combined stdout/stderr as
  the tool result. During that the spinner is paused.
- Run with `-b` or `--brave-mode` to skip the confirmation prompts and execute tools automatically.
