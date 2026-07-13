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
  * There is a `.env` file in the directory that provides the `SARA_API_KEY` variable (bearer to interact with the
    LLM Provider), the `SARA_MODEL` variable (which model to use), and the required `SARA_BASE_URL` variable that
    points to the OpenAI-compatible API base (without the trailing path). Optional: `SARA_SEARXNG_URL` and
    `SARA_SEARXNG_TOKEN` for web search (see Web Search below).
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
- Optional local overrides: `~/.config/sara/.env.local` (overrides `.env`)
- Optional system prompt file: `~/.config/sara/system-prompt.md`

Variables and precedence (later overrides earlier):

1) `.env` file values
2) `.env.local` file values
3) Real environment variables
4) Command line options (CLI) — currently only `--model`/`-m` overrides `SARA_MODEL`

Required variables (fail fast with a clear error if any is undefined after applying precedence):

- `SARA_MODEL`, `SARA_API_KEY`, `SARA_BASE_URL`

Optional variables:

- `SARA_SEARXNG_URL` — base URL of a Searxng instance. When set, SARA registers a `web_search` tool that queries
  `${SARA_SEARXNG_URL}/search?format=json`.
- `SARA_SEARXNG_TOKEN` — optional bearer token sent as `Authorization: Bearer <token>` to the Searxng instance. Ignored
  if `SARA_SEARXNG_URL` is not set.

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

# Searxng (optional web search)
# SARA_SEARXNG_URL=http://localhost:8080
# SARA_SEARXNG_TOKEN=optional-bearer-token
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
- Tool executors implement `suspend fun execute(...)`, so tools may perform suspending I/O (e.g., HTTP). The tool-call
  dispatch loop in `Sara.kt` is suspending and awaits each tool before appending the `role = "tool"` result message.

### Web Search (Searxng)

When `SARA_SEARXNG_URL` is set, SARA registers a `web_search` tool backed by the configured Searxng instance. The tool
calls `${SARA_SEARXNG_URL}/search?format=json&q=<query>` and returns all results from a single request page, formatted
as `Title`/`URL`/`Snippet` blocks. An optional bearer token can be supplied via `SARA_SEARXNG_TOKEN`. The previous
OpenRouter `web` plugin path has been removed.

## Self-Update Instruction

This guidelines file is a living document and MUST be actively maintained by the LLM Agent.

* **Trigger:** Whenever significant changes are made to the tech stack, project structure, coding guidelines, or key
  features, the LLM Agent MUST immediately update this file (`AGENTS.md`) to reflect the current state of the project.
* **Content:**
  * Add any information that could have helped the agent to solve the task more efficiently or in fewer steps.
  * Remove outdated, obsolete, or incorrect information.
  * Ensure all tech stack versions and library names are accurate.
  * Make sure the most important features are clearly documented.
  * Keep the project structure up to date so that the most important files and directories are visible at a glance.
* **Proactivity:** Do not wait for explicit instructions to update these guidelines if you identify a discrepancy
  between the guidelines and the actual codebase.
