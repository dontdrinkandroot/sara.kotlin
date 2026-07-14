## About

* This is the codebase of SARA (System Action & Response Agent), an LLM Agent Interface for the CLI that helps the user
  use their Linux/Unix operating system.
* It is written in Kotlin/Native 2.4.0
* It uses ktor 3.5.1 to handle the API requests against an OpenAI compatible API
* It uses ksoup 0.2.6 (fleeksoft) for HTML parsing in the `web_fetch` tool
* It uses mordant 3.0.2 for styling the output and formatting the Markdown output

## Coding Rules

* We adhere to Clean Code and SOLID principles.
* Remember that Clean Code implies using speaking variable and function names to avoid unnecessary comments.
* As we are SOLID, we keep an eye on testability as it will indicate a good separation.
* We love to use Kotlin sugar if the code remains readable or even helps it.

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

### Inspecting the System Prompt

The assembled system prompt can be inspected locally by running the `SystemPromptDumpTest`:

```bash
./gradlew nativeTest --tests "*.SystemPromptDumpTest.dumpSystemPrompt" --info
```

The test prints the fully assembled system prompt (persona, customizations, and live system information) to stdout.
Gradle suppresses task stdout by default, so pass `--info` (or `--rerun-tasks --info` to force a fresh run) to actually
see the `=== SYSTEM PROMPT START ===` ... `=== SYSTEM PROMPT END ===` block and the reported length in the build log.
The test is skipped in CI (output suppressed when `CI=true`), but the assertions still execute to validate the
provider chain works. The test excludes the user-provided `system-prompt.md` to avoid requiring config.

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

In addition, SARA automatically injects useful system context at startup via built‑in providers. Each section is
gathered independently and skipped gracefully if the underlying command/variable is unavailable (one failing section
never blanks the others).

If present, the user’s `system-prompt.md` is appended after these automatic sections. If the file is missing or empty,
only the automatic sections are used.

The assembled system prompt is built by `ChainedSystemPromptProvider` in `Main.kt` from four providers in this order:

1. `SaraSystemPromptProvider` — built-in persona ("You are Sara…").
2. `SystemCustomizationsProvider` — System Customizations Log (see below).
3. `StaticSystemPromptProvider(configuration.systemPrompt)` — the user’s `system-prompt.md` (skipped if absent/empty).
4. `SystemInformationSystemPromptProvider` — a `## System Information` block made of the sections below, assembled via
   `ChainedSystemPromptProvider` (so per-leaf failures are isolated by `safeProvide()`). All providers live in the
   `systemprompt.systeminformation` subpackage:

- `### General` — one line per field: Date (`date -Is`), Distribution (`/etc/os-release`),
  Architecture (`uname -m`), Package manager (`command -v` probe in priority order: apt, dnf,
  yum, pacman, zypper, apk, emerge, nix, rpm-ostree), Sudo (`sudo -n true` probe →
  `passwordless` / `requires-password` / `unavailable`), Current User (`$USER`),
  Home Directory (`$HOME`), Timezone (`/etc/timezone` or `date +%Z`), Shell (`$SHELL`),
  Locale (`$LC_ALL`/`$LANG`). Each leaf is a standalone, unit-testable class (e.g.
  `DistributionProvider.kt`, `SudoProvider.kt`, …); pure parsers are extracted as `internal fun`s
  (e.g. `parseDistribution`, `formatSudoStatus`, `detectPackageManager`).
- `Memory` — single compact line (`Memory: <total> total, <available> available`) from `free -h`
  (falls back to the `free` column when `available` is absent). See `MemorySectionProvider.kt`.
- `Root filesystem` — single compact line (`Root filesystem: <size>, <use%> used`) from `df -h /`.
  See `RootFsSectionProvider.kt`.
- `### CPU` — `lscpu` core count, with a `/proc/cpuinfo` fallback; emits `CPU(s): N` only
  (model name and per-core details are dropped as non-actionable). See `CpuSectionProvider.kt`.
- `### Current Directory` — `pwd` plus `ls -lA` listing (dotfiles included). See
  `CurrentDirectoryProvider.kt`.

The config directory resolver is the shared `defaultConfigDir()` helper in `configuration/Configuration.kt` (
`$HOME/.config/sara`, `.` fallback).

### System Customizations Log

SARA proactively maintains `~/.config/sara/system-customizations.md` as a curated, refactorable state document
describing how the system deviates from a default installation. It is **NOT** an append-only log: entries are added when
a change is made and deleted/updated when reverted, so the file always reflects the CURRENT state.

- Sections: `### Installed packages`, `### Removed/purged packages`, `### Configuration files`, `### Services`,
  `### Users and groups`, `### Scheduled tasks`, `### Other`.
- Entries are concise and factual (package name, config path + one-line description, service name + desired state);
  no timestamps, narration, or command transcripts.
- SARA creates the file/`~/.config/sara/` (`mkdir -p`) on first change. Only genuine deviations are recorded; read-only
  inspection commands and transient state are never logged.
- The file’s current contents are injected into the system prompt at session start by `SystemCustomizationsProvider`
  (truncated to ~10000 chars with `...[truncated]` to protect the context window), so the agent reconciles against the
  latest baseline before making further changes.

Implementation: `systemprompt/SystemCustomizationsProvider.kt`.

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

- The assistant can request tools; SARA exposes the following tools:
  - `exec_command` — run a local shell command (combined stdout/stderr). **unsafe** (always prompts).
  - `read_file` — read a file by path. **safe** (no prompt). Guarded by the sensitive-data policy in the system prompt.
  - `write_file` — write content to a file by path. **unsafe** (always prompts).
  - `web_fetch` — fetch a web page and return its content as Markdown, text, or HTML (always registered). **safe**.
  - `web_search` — search the web via Searxng (only registered when `SARA_SEARXNG_URL` is set). **safe**.
- Each `ToolExecutor` declares `val isSafe: Boolean` (default `false`). Safe tools (read-only, side-effect-free)
  bypass the confirmation prompt even when brave mode is off. Unsafe tools always prompt unless brave mode is on.
- When a tool is prompted and the user declines, the user may optionally provide a reason (press Enter to omit).
  The reason (if any) is included in the tool result message sent back to the LLM:
  `"Error: Tool execution denied by user. Reason: <reason>"` (or `"Error: Tool execution denied by user"` when omitted).
- Run with `-b` or `--brave-mode` to skip the confirmation prompts and execute tools automatically (overrides `isSafe`).
- Tool executors implement `suspend fun execute(...)`, so tools may perform suspending I/O (e.g., HTTP). The tool-call
  dispatch loop in `Sara.kt` is suspending and awaits each tool before appending the `role = "tool"` result message.
- The permission flow (`checkToolPermission`, `askForToolPermission`, `buildDenialMessage`) and `PermissionResult`
  are `internal` on `Sara` for testability. `InputReader` (a `fun interface`) abstracts stdin so the prompt logic
  is unit-testable without the REPL.

### Sensitive Data Policy

- `SaraSystemPromptProvider` injects a "Sensitive data policy" section into the system prompt that forbids SARA from
  reading, displaying, printing, transmitting, or exfiltrating private/secret material under any circumstances.
- Forbidden examples: `/etc/shadow`, SSH private keys, GPG private keys, cloud/SDK credentials.
- This is the soft guardrail that complements `read_file` being marked `isSafe = true` (executed without confirmation).
- The policy applies to `read_file`, `exec_command`, and `web_fetch` alike.

### Web Search (Searxng)

When `SARA_SEARXNG_URL` is set, SARA registers a `web_search` tool backed by the configured Searxng instance. The tool
calls `${SARA_SEARXNG_URL}/search?format=json&q=<query>` and returns all results from a single request page, formatted
as `Title`/`URL`/`Snippet` blocks. An optional bearer token can be supplied via `SARA_SEARXNG_TOKEN`. The previous
OpenRouter `web` plugin path has been removed.

### Web Fetch

SARA always registers a `web_fetch` tool (no configuration required). It fetches the content of a web page via HTTP
and returns it to the LLM in a specified format. The tool uses a realistic browser User-Agent to avoid being blocked
by common sites.

Arguments:

- `url` (string, required) — absolute URL to fetch.
- `format` (string, optional, enum: `markdown` | `text` | `html`, default `markdown`) — output format.
  - `markdown`: HTML is parsed with Ksoup, noise elements (script, style, nav, header, footer, aside, noscript, svg,
    form, iframe) are stripped, and the remaining DOM is converted to Markdown preserving headings, links, lists, code
    blocks, blockquotes, and tables. Relative links are resolved against the page URL.
  - `text`: HTML is parsed and `body.text()` is returned as plain text.
  - `html`: raw HTML body is returned as-is.
- `max_length` (integer, optional, default 50000) — hard cap on returned characters to protect the context window.

The returned content is prefixed with the URL and page title. If the content exceeds `max_length`, it is truncated
with a `...[truncated]` marker.

Implementation: `WebFetchClient` (ktor HTTP GET), `HtmlToMarkdown` (Ksoup DOM → Markdown converter, unit-testable
without network), `WebFetchTool` (tool executor).

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
