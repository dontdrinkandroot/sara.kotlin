# Backlog

Known bugs, improvement areas, and feature options, grouped by category and ordered by
priority within each section. When an item is resolved, remove it from this file (and
update `AGENTS.md` if the behavior changed). When a category empties out, remove its
heading too. Origin: technical/domain review of 2026-07-20.

## Bugs

### Conversation integrity

- **Silent drop of reasoning-only responses** (`Sara.displayAssistantMessage`): when the
  model returns neither content nor tool calls (e.g. reasoning-only, or truncated with
  `finish_reason: length`), the turn just ends with a debug log — the user sees nothing
  and no message is appended. Surface a visible message to the user in this case.

### HTTP client robustness

- **`LlmApiException` kills the session** (`Sara.fetchLlmResponse`): any 429/500/network
  error propagates out of the conversation loop and terminates the REPL, losing the whole
  conversation. Catch per turn, show the error to the user, and let the session continue
  (optionally with retry/backoff for transient errors).
- **Over-strict response schema** (`ChatCompletionResponse`): `id`, `created`, `model`,
  `object` are non-nullable. `ignoreUnknownKeys` does not protect against *missing* fields
  — some compatible providers omit them, causing a deserialization crash. Give them
  defaults (e.g. `""` / `0`).
- **Dead API surface** (`LlmClient.chatCompletion`): `maxTokens`, `temperature`, `topP`,
  penalties and `toolChoice` are plumbed through but never set from anywhere. Either wire
  them to configuration (env/CLI) or remove them.

### exec_command

- **Exit code swallowed** (`executeCommand`, `ExecuteCommand.kt`): the return value of
  `pclose` is ignored, so a failing command with no output reports "Command executed
  successfully with no output" — actively misleading the LLM. Capture the exit status and
  include it in the result.
- **Unbounded output**: unlike `web_fetch` and `read_file`, there is no truncation. A
  command like `cat hugefile` or `find /` explodes the context window and the API bill.
  Truncate with a continuation marker, consistent with the other tools.

### Security

- **Plan mode bypassable at execution time** (`Sara.executeToolCall`): `write_file` is
  removed from the tool *schema* in plan mode, but execution never re-checks
  `availableInPlanMode`. If the model emits the tool anyway (or the mode switched
  mid-turn), it executes. Add a defense-in-depth check at dispatch time.
- **`web_fetch` has no SSRF guard** (`WebFetchClient`): the model can fetch
  `http://169.254.169.254/` (cloud metadata), `http://localhost:*`, or other internal
  URLs — undermining the sensitive-data policy. Add a scheme allowlist plus a
  private/loopback IP block (or at least a config flag).
- **Sensitive-data policy is prompt-only**: `read_file` is `isSafe = true` (no
  confirmation), so a confused or jailbroken model reading `~/.ssh/id_rsa` faces zero
  technical friction. Add a path blocklist to `ReadFileTool` (and possibly to
  `exec_command` pattern matching) as defense-in-depth.
- **SIGINT handler is not async-signal-safe** (`SignalInterruptSource`): storing an
  `AtomicReference` and calling `Job.cancel()` inside a POSIX signal handler is undefined
  behavior (only async-signal-safe functions may be called) and can deadlock or crash. The
  classic fix is the self-pipe trick.

### Tools

- **`read_file` fake pagination** (`ReadFileTool.readRange`): the entire file is read into
  memory via `fgets` and only then sliced by offset/limit — pagination is pointless for
  large files (memory + slow). Skip to `offset` while reading and stop after `limit`
  characters.
- **`web_fetch` parses HTML twice** (`WebFetchTool.execute`): `HtmlToMarkdown.convert` and
  `extractTitle` each parse the document. Parse once and reuse.
- **`write_file` ignores partial writes** (`WriteFileTool.writeWholeFile`): `fwrite` is
  called once without looping. Usually fine for regular files, but technically incorrect.

### Configuration

- **Env parsing quirks** (`Env.kt`): `readProcessEnv` reuses the dotenv parser, which
  strips surrounding quotes from *real* environment variable values (dotenv semantics
  wrongly applied to the process env), and reads `/proc/self/environ` one byte at a time
  via `fgetc`. Use a separate parser for process env and read in blocks.
- **`defaultConfigDir()` fallback conflict** (`Configuration.kt`): when `$HOME` is unset
  it falls back to relative `Path(".")`, but `readEnv` then throws on non-absolute paths.
  Make the edge case consistent.

### Docs & hygiene

- **AGENTS.md ordering mismatch**: the documented system-prompt chain order (user prompt
  *after* system information) contradicts `Main.kt` (user prompt *before* system
  information). Fix whichever is wrong.
- **Leftover empty template dirs**: `src/test/kotlin/org/example` and
  `src/nativeMain/net/...` are unused template remnants. Delete them.

## Improvements

### Context & cost

- **No context management**: conversation history grows unboundedly and the full system
  prompt is re-sent every turn. Add token estimation plus compaction/summarization, or at
  least a warning threshold. Related: `Usage` is parsed from responses but never shown —
  a per-turn token display is nearly free.

### Architecture

- **`Sara` is becoming a god class**: REPL, mode state, permission flow, tool dispatch,
  and rendering all live in one class. Extract a `ConversationSession` (message history)
  and a `ToolDispatcher`.
- **Hand-built JSON schemas in every tool**: each tool assembles its parameter schema with
  raw `buildJsonObject` calls — repetitive and error-prone. A small shared schema-builder
  DSL would shrink each tool significantly.

### Robustness

- **No timeouts or retries anywhere**: `LlmClient`, `WebFetchClient`, and `SearxngClient`
  rely on ktor defaults and can hang for a long time; `exec_command` has no timeout at
  all, so a model running an interactive command (`apt`, `tail -f`) blocks the turn until
  Ctrl+C. Add sane timeouts everywhere and retry with backoff for transient HTTP errors.

### UX

- **Bare REPL input**: `readlnOrNull()` provides no line editing, no history, no
  multi-line input. At minimum, persist a history file under `~/.config/sara/`.
- **No test around the turn loop**: parsers and permission flow are tested, but the turn
  loop itself (dangling tool calls, error recovery) is exactly where the live bugs are.
  Add tests there.

## Feature Options

### High value / low effort

- **One-shot mode**: `sara "why is disk full"` or `echo "..." | sara` — run a single
  non-interactive turn, print the answer, exit. Huge for scripting; the REPL already
  isolates a turn, so this is mostly a new entry point.
- **Token/cost display**: show `Usage` (prompt/completion/total tokens) per turn in
  verbose mode or via a `/cost` command; optionally OpenRouter's cost extension.
- **`exec_command` hardening**: capture exit code, truncate output at N chars, configurable
  timeout (e.g. `SARA_EXEC_TIMEOUT`).
- **Slash-command framework**: formalize `/plan` and `/exec`, add `/help`, `/clear`
  (reset conversation), `/model <name>` (runtime switch), `/brave` toggle.
- **Configurable model params**: `SARA_MAX_TOKENS`, `SARA_TEMPERATURE` — the plumbing in
  `LlmClient` already exists.

### Medium effort

- **Streaming responses** (`stream = true`, SSE): much better perceived latency; the
  spinner is currently a workaround for this.
- **Context compaction**: summarize or truncate old turns when approaching the model's
  context limit.
- **Session persistence**: save/load conversations (`/save <name>`, `sara --resume`),
  stored under `~/.config/sara/sessions/`.
- **More native tools**: `list_directory`, `grep`/`search_files`, `glob` — reduces
  `exec_command` usage and improves safety, matching the persona's "prefer native tools"
  rule.
- **Technical enforcement of the sensitive-data policy**: path blocklist in `read_file`,
  URL/IP blocklist in `web_fetch` (see Security bugs above).

### Bigger bets

- **MCP client support**: tool extensibility without recompiling.
- **Auto-approve list**: per-tool or per-command-pattern allowlist (e.g. always allow
  `ls`, `git status`) instead of all-or-nothing brave mode.
- **Prompt caching**: use provider prompt-caching (Anthropic/OpenRouter) to cut the cost
  of the large system prompt.
- **Multi-platform targets**: macOS/Windows support; the build currently hard-fails on
  non-Linux hosts, and most POSIX code would port to macOS easily.
