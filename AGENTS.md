## About

* This is the codebase of SARA (System Action & Response Agent), an LLM Agent Interface for the CLI that helps the user
  use their Linux/Unix operating system.
* It is written in Kotlin/Native 2.4.20
* It uses ktor 3.5.2 to handle the API requests against an OpenAI compatible API
* It uses ksoup 0.2.6 (fleeksoft) for HTML parsing in the `web_fetch` tool
* It uses mordant 3.1.0 for styling the output and formatting the Markdown output

## Coding Rules

* We adhere to Clean Code and SOLID principles.
* Remember that Clean Code implies using speaking variable and function names to avoid unnecessary comments.
* As we are SOLID, we keep an eye on testability as it will indicate a good separation.
* We love to use Kotlin sugar if the code remains readable or even helps it.
* We use test-driven development by default, in small red→green→refactor cycles writing tests first, checking if they fail as expected, then implementing. The tests are documenting our specification and expectations. If a test would be overly complicated, ask the user first if it is worth it.

## Environment

* The configuration is stored in `~/.config/sara/`
  * There is a `.env` file in the directory that provides the `SARA_API_KEY` variable (bearer to interact with the
    LLM Provider), the `SARA_MODEL` variable (which model to use), and the required `SARA_BASE_URL` variable that
    points to the OpenAI-compatible API base (without the trailing path). Optional: `SARA_SEARXNG_URL` and
    `SARA_SEARXNG_TOKEN` for Searxng web search (see Web Search below), and `SARA_EXA_API_KEY` for the Exa web search &
    contents tools (see Exa Web Search & Contents below).
    * There is an optional `system-prompt.md` file that contains a System Prompt which is prepended to each session.
    * The current session is persisted to `session.json` (continuously, survives clean exits) and offered for restore
      at the next startup (see Session persistence & restore below); `session.json.bak` holds a quarantined corrupt
      session file.

## Build

* The result is a binary called `sara.kexe` that can be built with `gradle build`.
* We always run `gradle build` after performing a task to see if the build succeeds and the tests are green.
* The `io.github.ben-manes.versions` Gradle plugin (0.62.0) provides the `gradle dependencyUpdates` task that reports
  outdated dependencies and Gradle releases; there is a matching `dependencyUpdates` run configuration.

## Git

* There is a `gitCommit` run configuration (`git commit {args}`) for creating commits; commit messages follow
  [Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat: add X`, `fix: handle Y`, `chore: bump deps`).
* Commit **only if the user explicitly requests it**, and **never push**.

## Testing

* We use the `kotlin-test` framework for testing.

### Inspecting the System Prompt

The assembled system prompt can be inspected locally by running the `SystemPromptDumpTest`:

```bash
./gradlew nativeTest --tests "*.SystemPromptDumpTest.dumpSystemPrompt" --info
```

The test prints the fully assembled system prompt (instructions, customizations, and live system information) to stdout.
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
- `SARA_EXA_API_KEY` — Exa API key. When set, SARA registers the `exa_search` and `exa_contents` tools backed by the Exa
  Search/Contents APIs (see Exa Web Search & Contents below).

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

# Exa (optional web search & contents; https://exa.ai)
# SARA_EXA_API_KEY=your-exa-api-key
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

1. `InstructionsProvider` — the built-in agent instructions, composed via a `ChainedSystemPromptProvider`
   of the root-level `##` sections in the `systemprompt.providers` package (`AboutProvider`,
   `SensitiveDataPolicyProvider`, `ModesProvider`, and the dynamic
   `WebToolUsageProvider`). The `## Web tool usage` section is generated from the tools that are actually
   registered: it nudges the agent to prefer the available web tools (`web_search`/`web_fetch` and/or `exa_search`/
   `exa_contents`) for unfamiliar, fast-moving, or version-specific topics (and to skip them for stable, well-known
   facts or when the answer is already available locally), preferring Exa tools over `web_search` when both are present.
   It is omitted entirely when no web tools are available.
2. `SystemCustomizationsProvider` — System Customizations Log (see below).
3. `StaticSystemPromptProvider(configuration.systemPrompt)` — the user’s `system-prompt.md` (skipped if absent/empty).
4. `SystemInformationProvider` — a `## System Information` block made of the sections below, assembled via
   `ChainedSystemPromptProvider` (so per-leaf failures are isolated by `safeProvide()`). All providers live in the
   `systemprompt.providers.systeminformation` subpackage:

- `### General` — one line per field: Date (`date -Is`), Distribution (`/etc/os-release`),
  Architecture (`uname -m`), Package manager (`command -v` probe in priority order: apt, dnf,
  yum, pacman, zypper, apk, emerge, nix, rpm-ostree), Sudo (`sudo -n true` probe →
  `passwordless` / `requires-password` / `unavailable`), Current User (`$USER`),
  Home Directory (`$HOME`), Timezone (`/etc/timezone` or `date +%Z`), Shell (`$SHELL`),
  Locale (`$LC_ALL`/`$LANG`). Each leaf is a standalone, unit-testable class (e.g.
  `DistributionProvider.kt`, `SudoProvider.kt`, …); pure parsers are extracted as `internal fun`s
  (e.g. `parseDistribution`, `formatSudoStatus`, `detectPackageManager`).
- `Memory` — single compact line (`Memory: <total> total, <available> available`) from `free -h`
  (falls back to the `free` column when `available` is absent). See `MemoryProvider.kt`.
- `Root filesystem` — single compact line (`Root filesystem: <size>, <use%> used`) from `df -h /`. See
  `RootFsProvider.kt`.
- `### CPU` — `lscpu` core count, with a `/proc/cpuinfo` fallback; emits `CPU(s): N` only (model name and per-core
  details are dropped as non-actionable). See `CpuProvider.kt`.
- `### Current Directory` — `pwd` plus `ls -lA` listing (dotfiles included). See
  `CurrentDirectoryProvider.kt`.

Layout convention: the `systemprompt` package holds only the `SystemPromptProvider` contract and the generic composing
machinery (`ChainedSystemPromptProvider`, `safeProvide()`, `cmd()`, and `StaticSystemPromptProvider`). Every concrete
provider lives under `systemprompt.providers`, nested to mirror the heading tree — root `##` sections sit directly in
`providers/`, `## System Information` children under `providers/systeminformation/`, and the `### General` leaves under
`providers/systeminformation/general/` — and each class is named after the section (or line) it renders.

The config directory resolver is the shared `defaultConfigDir()` helper in `configuration/Configuration.kt` (
`$HOME/.config/sara`, `.` fallback).

### System Customizations Log

SARA proactively maintains `~/.config/sara/system-customizations.json` as a curated, refactorable state document
describing how the system deviates from a default installation. It is **NOT** an append-only log: entries are added when
a change is made and deleted/updated when reverted, so the file always reflects the CURRENT state.

- Storage is pretty-printed JSON with the 7 fixed sections as top-level keys (`installedPackages`, `removedPackages`,
  `configurationFiles`, `services`, `usersAndGroups`, `scheduledTasks`, `other`), each holding `{id, entry}` objects,
  plus a `nextId` counter. Entries are addressed by stable sequential integer IDs.
- The agent never edits the file manually. Three dedicated tools (all `isSafe = true`, exec mode only) mutate it via
  `SystemCustomizationsStore`:
  - `add_customization(section, entry)` — idempotent (case-insensitive trimmed dedupe per section), returns the assigned
    ID.
  - `remove_customization(id)` — removes by ID (used when a change is reverted).
  - `replace_customization(id, entry)` — atomic text update, keeps the ID.
  - Every mutation result includes the full freshly rendered state so the agent always has current IDs.
- Entries are concise and factual (package name, config path + one-line description, service name + desired state); no
  timestamps, narration, or command transcripts. Only genuine deviations are recorded; read-only inspection commands and
  transient state are never logged.
- The store creates the file/`~/.config/sara/` on first write, tolerates missing/corrupt files (falls back to empty
  state), and repairs entries with missing/duplicate IDs on load.
- The file's current contents are injected into the system prompt at session start by `SystemCustomizationsProvider`,
  rendered as `### <Section>` / `- [<id>] <entry>` (empty sections omitted, truncated to ~10000 chars with
  `...[truncated]`), so the agent reconciles against the latest baseline and can address entries by ID.

Implementation: `customizations/CustomizationSection.kt` (7-section enum), `customizations/Customizations.kt`
(serializable model + render), `customizations/SystemCustomizationsStore.kt`,
`tool/{Add,Remove,Replace}CustomizationTool.kt`, `systemprompt/providers/SystemCustomizationsProvider.kt`.

### Session persistence & restore

The current session is continuously persisted to `~/.config/sara/session.json` so an interrupted, crashed, or rebooted
session can be continued at the next startup.

- File layout: pretty-printed JSON with `version` (currently 1), `savedAt` (ISO 8601 via `date -Is`), `mode`
  (`exec`/`plan`), and `messages` — the **verbatim** conversation (`Message` is already `@Serializable`), including
  system messages, assistant messages with `tool_calls`, and all `tool` results.
- The file is rewritten after **every** conversation mutation (user message, assistant message, each tool result,
  denial/interrupt synthetic results, mode switch, interrupt system message) via `Sara.persistSession`. It **survives
  clean exits**, so every startup that finds one offers to continue it.
- Startup: if a saved session exists, SARA prints `Found a previous session (last activity <savedAt>)` and asks
  `Continue previous session? [y/N]` — **Enter/N starts a new session** (the file is deleted immediately; it reappears
  with the first save of the new session). `y`/`yes` restores the saved state **verbatim**: the fresh system message is
  replaced by the saved message list and `currentMode` is taken from the file (no duplicate mode-switch system message
  is injected).
- A **corrupt** file is renamed to `session.json.bak` (preserved for manual inspection, overwriting a previous backup)
  and reported with a yellow warning; the session then starts fresh.
- Failures while saving are logged but never break the running turn.

Implementation: `session/SavedSession.kt` (serializable model + `savedModeOrDefault` fallback for unknown mode names),
`session/SessionStore.kt` (interface with sealed `SessionLoad` result: `NoSession` / `Found` / `Corrupt`),
`session/FileSessionStore.kt` (file-backed implementation). The store is a **required** constructor dependency of
`Sara` (no hidden default that would touch the real config dir in tests); `Main.kt` wires `FileSessionStore()`, tests
use `FakeSessionStore` (`nativeTest`). Covered by `FileSessionStoreTest` and `SaraSessionTest`.

### CLI Interaction

- Interactive REPL: the prompt reads input via a raw-mode line editor (see Prompt Line Editor
  below), falling back to cooked `readlnOrNull()` when raw mode is unavailable (e.g. piped IO).
- Session ends when the user submits an empty line or EOF (Ctrl+D) is encountered.
- **Multiline input**: bracketed paste mode (`ESC[?2004h`) is enabled at startup, so pasting
  multiline text submits it as ONE message (Mordant's POSIX parser reports the paste markers as
  the `PasteStart`/`PasteEnd` keys; the editor inserts pasted characters verbatim and embedded
  line breaks do NOT submit). **Alt+Enter** inserts a newline; a line ending in `\` continues on
  the next line (the backslash is dropped).
- **Ctrl+C (SIGINT) interrupts** the current turn (LLM request, tool execution, or permission prompt) and
  returns to the prompt. A second Ctrl+C force-exits the process (safety net for stuck blocking calls; the
  signal handler force-exits when it fires while the flag is still pending, e.g. double Ctrl+C at the prompt).
  At the prompt, raw mode reports Ctrl+C as a key event; SARA aborts the current input and re-raises
  SIGINT via `kill(getpid(), SIGINT)` (`Main.raiseProcessInterrupt`) so the handler semantics stay intact;
  the raised flag is consumed at the next turn start (`runTurnWithInterrupt`) and never exits the app on its own.
- When a turn is interrupted, a system message (`"The user interrupted this turn. Stop and wait for the next user
  message."`) is appended to the conversation so the LLM reconciles the partial state. If the interrupt happens during
  tool-call processing, every unanswered tool call additionally receives a synthetic `tool` result
  (`"Error: Tool execution interrupted by user (Ctrl+C)"`) so no dangling `tool_calls` remain that would make the next
  API request fail with a 400 (see `Sara.processToolCalls`).
- The SIGINT handler is installed via `SignalInterruptSource` (POSIX `signal()`). The `InterruptSource` interface
  is injected into `Sara` for testability (tests use `FakeInterruptSource`).
- Verbose mode (-v/--verbose) prints a short REPL start hint.

### Prompt Line Editor

The REPL prompt is powered by the `editor` package, a small stack kept free of Mordant types so the
editing semantics are unit-testable:

- `EditEvent` / `EditResult` / `LineBuffer` / `LineEditor` — pure state machine: maps an edit event to
  the next buffer or a terminal outcome (`Submit` / `Abort` / `Eof`). Enter submits, a trailing `\`
  continues on the next line (backslash dropped), paste markers are no-ops on the buffer, and Ctrl+D
  ends input on an empty buffer / deletes forward otherwise (bash convention).
- `keyEventToEditEvent` (`MordantLineInput.kt`) — maps Mordant `KeyboardEvent`s (MDN key names:
  `ArrowLeft`, `Home`, `Enter`, …) to edit events; unknown keys are skipped. Single-char `\n`/`\r`
  keys insert newlines (they occur inside bracketed pastes).
- `RenderTarget` / `PromptRenderer` / `MordantRenderTarget` — block renderer: redraws every row
  of the (possibly multiline) buffer, erasing exactly the previously rendered rows, so
  continuation lines leave no stale text; cursor positioning is logical (prefix width + column).
  Row transitions to *new* rows use CR+LF (`moveToNextLine`) because cursor-down clamps at the
  bottom screen margin instead of scrolling — at a bottom-of-window prompt the continuation row
  would otherwise overwrite the current row; moves within the rendered block use plain cursor
  movements.
- `MordantLineInput` — the `LineInput` implementation: opens raw mode per read
  (`enterRawModeOrNull` → `RawModeScopeReader`), renders the buffer with cursor positioning
  (`startOfLine(); clearLine()` + cursor-left to the logical cursor), and translates Ctrl+C into an
  `Abort` plus the injected `raiseInterrupt` hook.
- `LineInput` / `LineReadResult` — the abstraction `Sara.promptUserInput` consumes
  (`Submitted` / `Aborted` / `Eof`); `ScriptedLineInput` replays scripted lines for tests.
- `BracketedPasteMode` — enables/disables `ESC[?2004h`/`ESC[?2004l` (idempotent); wired in `Main.kt`.
- `PasteMarker.kt` — pure recognizer for the `ESC[200~`/`ESC[201~` byte sequences (kept for
  potential cooked-mode paste detection; unused by the raw-mode path).

There is NO command history (deliberate scope decision); logical rows never re-wrap: a logical
line longer than the terminal width wraps visually and is edited as one row (cursor movement
still operates on the logical buffer).

### Plan / Execution Mode

SARA supports two operational modes that control how the agent interacts with the system:

- **Execution mode** (`exec`) — the default. All tools are available and the agent may perform
  system modifications.
- **Plan mode** (`plan`) — read-only mode. The `write_file` tool is excluded from the tool list
  sent to the LLM (hard guardrail). Additionally, a system message instructs the agent not to
  modify the system state through any tool, including `exec_command` — which in plan mode is
  restricted to read-only inspection commands (no `rm`, `mv`, `mkdir`, `apt install`,
  `systemctl start/stop/enable`, `chmod`, `chown`, …). It should only read, analyze, observe,
  and plan.

Switching:

- Type `/plan` at the prompt to enter plan mode.
- Type `/exec` at the prompt to return to execution mode.
- The mode is shown in the prompt: `User [plan]:` or `User [exec]:`.
- Switching to the current mode is a no-op (no duplicate system message).
- On mode switch, a system message is appended to the conversation so the LLM is aware of the
  constraint. The mode resets to `exec` on each new session.

Tool availability per mode:

| Tool                    | exec | plan   |
|-------------------------|------|--------|
| `exec_command`          | yes  | yes    |
| `read_file`             | yes  | yes    |
| `write_file`            | yes  | **no** |
| `web_fetch`             | yes  | yes    |
| `web_search`            | yes  | yes    |
| `add_customization`     | yes  | **no** |
| `remove_customization`  | yes  | **no** |
| `replace_customization` | yes  | **no** |

Each `ToolExecutor` declares `val availableInPlanMode: Boolean` (default `true`). Tools that
can modify the system (e.g. `write_file`) override this to `false`. The `ToolRegistry.getToolSchemas`
method accepts an optional filter predicate used by `Sara` to exclude plan-unsafe tools.

Implementation: `Mode.kt` (enum with `label` and `instruction`), `ToolExecutor.availableInPlanMode`,
`WriteFileTool` (override), `ToolRegistry.getToolSchemas(filter)`, `Sara.kt` (mode state, prompt,
`/plan`/`/exec` handling, tool filtering in `fetchLlmResponse`).

The modes are also described statically in `InstructionsProvider` (a `## Modes` section in the
base system prompt) so the agent always knows modes exist and can suggest `/plan` or `/exec`. The
dynamic `Mode.instruction` injected on each switch is a pointed reminder of the *current* mode's
constraints — for PLAN it enumerates forbidden `exec_command` actions explicitly.

### Verbose Debug Logging

Run with `-v` or `--verbose` to enable debug output (e.g., config summary, system prompt length, and detailed tool call
information).

### Progress indicator

- While waiting for the LLM response a transient status line is shown using Mordant's Terminal. The indicator is scoped
  to the model request and stops automatically before any prompts or tool execution. No manual pause/resume is needed.

### Markdown Rendering

- LLM responses are rendered as Markdown in the terminal using Mordant 3.1.0.

### Tool Calling Support

- The assistant can request tools; SARA exposes the following tools:
  - `exec_command` — run a local shell command (combined stdout/stderr). Returns the output
    (truncated to head/tail excerpts when oversized) plus its terminal status
    (`[exit code: N]` or `[killed by signal: SIGX]`). **unsafe** (always prompts). See
    `exec_command` result format below.
  - `read_file` — read a file by path. **safe** (no prompt). Guarded by the sensitive-data policy in the system prompt.
    Optional arguments: `offset` (0-based character offset to start at, default 0) and `limit` (max characters to
    read, default 10000) to keep large files from polluting the context window. When the file has more content
    beyond the returned window, a `...[truncated, continue with offset=<nextOffset>, file has <total> characters]`
    marker is appended so the agent can paginate by setting `offset` to the next offset. If `offset` is at or beyond
    the end of file, a `[offset <N> is at or beyond end of file (<total> characters)]` footer is returned instead.
  - `write_file` — write content to a file by path. **unsafe** (always prompts).
  - `web_fetch` — fetch a web page and return its content as Markdown, text, or HTML (registered unless
    `SARA_EXA_API_KEY` is set, which disables it in favor of `exa_contents`). **safe**.
  - `web_search` — search the web via Searxng (only registered when `SARA_SEARXNG_URL` is set). **safe**.
  - `exa_search` — search the web via Exa and return ranked results with highlights (only registered when
    `SARA_EXA_API_KEY` is set). **safe**.
  - `exa_contents` — extract clean, LLM-ready content from a web page via Exa (only registered when
    `SARA_EXA_API_KEY` is set). **safe**.
  - `add_customization` / `remove_customization` / `replace_customization` — maintain the system customizations record
    (see System Customizations Log above) by section/ID. **safe** (no prompt), exec mode only.
  - `InstructionsProvider` explicitly encourages eager use of the available web
    tools for unfamiliar or version-specific topics, while skipping them for stable, well-known facts or when the local
    system already provides the answer, and prefers
    `exa_search`/`exa_contents` over `web_search` when they are available.
- Each `ToolExecutor` declares `val isSafe: Boolean` (default `false`). Safe tools (read-only, side-effect-free)
  bypass the confirmation prompt even when brave mode is off. Unsafe tools always prompt unless brave mode is on.
- Tool calls executed without a prompt (brave mode or safe tools) are announced in the terminal with a compact yellow
  one-liner (`[sara] Executing tool '<name>': <args>`), with arguments truncated to ~120 characters, so tool usage stays
  transparent. Implementation: `Sara.announceToolExecution` (called from
  `checkToolPermission`) and the pure `internal fun formatToolAnnouncement` in `Sara.kt` (tested in
  `ToolAnnouncementTest`).
- Each `ToolExecutor` also declares `val availableInPlanMode: Boolean` (default `true`). Tools that can modify the
  system (e.g. `write_file`) override this to `false` so they are excluded in plan mode.
- When a tool is prompted and the user declines, the user may optionally provide a reason (press Enter to omit).
  The reason (if any) is included in the tool result message sent back to the LLM:
  `"Error: Tool execution denied by user. Reason: <reason>"` (or `"Error: Tool execution denied by user"` when omitted).
- Run with `-b` or `--brave-mode` to skip the confirmation prompts and execute tools automatically (overrides `isSafe`).
- Tool executors implement `suspend fun execute(...)`, so tools may perform suspending I/O (e.g., HTTP). The tool-call
  dispatch loop in `Sara.kt` is suspending and awaits each tool before appending the `role = "tool"` result message.
- The permission flow (`checkToolPermission`, `askForToolPermission`, `buildDenialMessage`) and `PermissionResult`
  (with `Allowed`, `Denied`, and `Interrupted` variants) are `internal` on `Sara` for testability. `InputReader`
  (a `fun interface`) abstracts stdin so the prompt logic is unit-testable without the REPL. `InterruptSource`
  abstracts the SIGINT mechanism so interrupt behavior is testable with `FakeInterruptSource`.
- The tool-call dispatch loop checks `coroutineContext.ensureActive()` at the top of each iteration (both the
  model-response loop and the tool-call loop), so a Ctrl+C interrupt between tool calls is caught promptly.
- `fetchLlmResponse` wraps the ktor request in a `coroutineScope` with a Mordant progress-spinner
  child job launched via `progress.execute()`. Cleanup happens in a `finally` block wrapped in
  `withContext(NonCancellable)` so the spinner is always torn down even when Ctrl+C cancels the
  turn mid-request: the progress task is marked complete (`progress.update { completed = 1 }`),
  the execute job is `join`ed (it exits naturally because the task is finished — do NOT use
  `cancelAndJoin`, which leaves Mordant's terminal interceptor installed and the cursor hidden),
  and finally `progress.clear()` removes the frame, uninstalls the interceptor, and restores the
  cursor. Never bypass Mordant with a manual `terminal.cursor.move { ... }` clear: that leaves the
  interceptor in place and causes the spinner to be re-drawn on every subsequent print.

### exec_command Result Format

The `exec_command` tool reports the command's terminal status on every result — the LLM no
longer has to guess whether an empty output meant success.

- `ExecuteCommand.kt` captures the `pclose` wait status and decodes it via
  `decodeWaitStatus` into an `ExitStatus` (`code` XOR `signal`; core-dump bit ignored,
  signal names from a static 1–31 map). `executeCommand()`/`executeCommandSafe()` keep
  their old signatures (10+ callers) and wrap the new `executeCommandWithStatus()`.
- stderr of **every** command in a compound command list (`a; b`, `a && b`, `a | b`) is
  captured: `popen` runs `sh -c "exec 2>&1; <command>"` — the shell redirects its own fd 2
  into the pipe *before* running the list. Do NOT revert to the naive `"<command> 2>&1"`:
  a redirection suffix binds only to the last simple command of a list, so stderr of all
  earlier commands would leak to SARA's terminal (regression covered by
  `ExecCommandToolTest`; the subshell alternative `( <command> ) 2>&1` is worse — it
  breaks on empty commands and trailing comments).
- The result is a `ToolResult.CommandResult(output, exitStatus, truncation)`; its
  `toContentString()` renders via `renderExecResult`:

  ```
  <output or (no output) placeholder>

  [exit code: 0]           # or [killed by signal: SIGSEGV]
  ```

- Oversized output (>20000 chars) is truncated to head+tail excerpts of ≤10000 chars each,
  snapped to line boundaries when a newline falls within the excerpt window (kept raw
  otherwise, so excerpts never exceed the limits). The full output is spilled to a
  `mkstemp`-created `/tmp/sara-exec-XXXXXX.log` (mode 0600); SARA never deletes spill files
  (the /tmp lifecycle handles them). The inline marker
  `...[N characters omitted — full log: /tmp/sara-exec-…; use read_file to inspect]...`
  plus a trailing `[output truncated: …]` footer point the agent to the spill file.
- Implementation: `ExecOutputFormatting.kt` (pure, fully unit-tested:
  `ExecOutputFormattingTest`) and `ExecCommandTool.kt` (I/O, covered by
  `ExecCommandToolTest` including exit code, signal death, placeholder, and spill-file
  round-trip). Test support: `SpillFileTestSupport.kt` (nativeTest).

### Sensitive Data Policy

- `SensitiveDataPolicyProvider` (a section of `InstructionsProvider`) injects a
  "Sensitive data policy" section into the system prompt that forbids SARA from
  reading, displaying, printing, transmitting, or exfiltrating private/secret material under any circumstances.
- Forbidden examples: `/etc/shadow`, SSH private keys, GPG private keys, cloud/SDK credentials.
- This is the soft guardrail that complements `read_file` being marked `isSafe = true` (executed without confirmation).
- The policy applies to `read_file`, `exec_command`, and `web_fetch` alike.
- **Accidental exposure must be reported**: if SARA accidentally reads or otherwise accesses secret
  material, it must immediately disclose this to the user — which file/command was involved, what data
  may have been exposed, and that the affected credentials must be treated as compromised and rotated.
  It must never hide or downplay the incident, and never repeat the exposed secret itself.

### Web Search (Searxng)

When `SARA_SEARXNG_URL` is set, SARA registers a `web_search` tool backed by the configured Searxng instance. The tool
calls `${SARA_SEARXNG_URL}/search?format=json&q=<query>` and returns all results from a single request page, formatted
as `Title`/`URL`/`Snippet` blocks. An optional bearer token can be supplied via `SARA_SEARXNG_TOKEN`. The previous
OpenRouter `web` plugin path has been removed.

### Exa Web Search & Contents

When `SARA_EXA_API_KEY` is set, SARA registers two tools backed by the Exa Search/Contents APIs (https://exa.ai):

- `exa_search` — POSTs `${EXA_BASE_URL}/search` with an `Authorization: Bearer <apiKey>` header and the query in
  `highlights` mode (the content mode Exa recommends for agent workflows), returning ranked `Title`/`URL`/
  `Published`/`Highlight` blocks. `num_results` defaults to 10.
- `exa_contents` — POSTs `${EXA_BASE_URL}/contents` for a single URL and returns the clean markdown content of the page
  (`URL`/`Title` headers, then the extracted text), truncated at `max_length` (default 50000) with a
  `...[truncated]` marker. Exa handles JavaScript-rendered pages, PDFs, and complex layouts.

Both tools are `isSafe = true`. Implementation: `ExaClient` (ktor), `ExaSearchTool`, `ExaContentsTool`, with
serialization covered by `ExaSerializationTest` and safe-ness asserted in `ToolExecutorIsSafeTest`. The
`InstructionsProvider` prefers
these tools over `web_search` when `SARA_EXA_API_KEY` is set. When Exa is enabled, `web_fetch` is **not**
registered (its role is taken over by `exa_contents`); Searxng's `web_search` may still coexist with Exa.

### Web Fetch

SARA registers a `web_fetch` tool by default (no configuration required), unless `SARA_EXA_API_KEY` is set (in which
case `exa_contents` covers page fetching). It fetches the content of a web page via HTTP
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

## Backlog

`BACKLOG.md` at the repo root lists known bugs, improvement areas, and feature options,
grouped by category and ordered by priority within each section. Check it before starting
a change; when an item is resolved, remove it from `BACKLOG.md` and update this file if
the behavior changed.

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
