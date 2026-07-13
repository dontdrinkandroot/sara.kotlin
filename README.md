### Sara (Kotlin/Native)

SARA (System Action & Response Agent) is a small, fast command-line assistant written in Kotlin/Native. It connects to
an OpenAI-compatible LLM API and provides a terminal-based experience with optional tools, a configurable system
prompt, and automatic injection of useful system context.

- Runs as a single native executable (no JVM required)
- Targets Linux (x64)
- Renders LLM responses as Markdown in the terminal
- Supports tool calling (`exec_command`, `read_file`, `write_file`, `web_fetch`, and optional `web_search`)

#### Download

Download prebuilt binaries from GitHub Releases:

- https://github.com/dontdrinkandroot/sara.kotlin/releases

Pick the asset for your platform, make it executable, and run it. Example (Linux):

```
curl -L -o sara https://github.com/dontdrinkandroot/sara.kotlin/releases/download/<version>/sara-<your-os-arch>
chmod +x sara
./sara --help
```

Replace `<version>` and `<your-os-arch>` with the correct values for the release you want to use.

#### Configuration

Configuration is read from the user config directory `~/.config/sara/` and resolved with the following precedence
(later overrides earlier):

1. `~/.config/sara/.env` file values
2. `~/.config/sara/.env.local` file values (overrides `.env`)
3. Real environment variables (override both `.env` files)
4. Command line options (override everything)

Required values (SARA fails fast with a clear error if any is undefined):

- `SARA_MODEL` – model identifier to use
- `SARA_API_KEY` – API key (bearer) for the LLM provider
- `SARA_BASE_URL` – base URL of the OpenAI-compatible API, without a trailing path
  (e.g., `https://openrouter.ai/api/v1`)

Optional values:

- `SARA_SEARXNG_URL` – base URL of a Searxng instance. When set, SARA registers a `web_search` tool that queries
  `${SARA_SEARXNG_URL}/search?format=json`.
- `SARA_SEARXNG_TOKEN` – optional bearer token sent as `Authorization: Bearer <token>` to the Searxng instance. Ignored
  if `SARA_SEARXNG_URL` is not set.

Optional files:

- `~/.config/sara/system-prompt.md` – custom system prompt prepended to each session (absence is not an error).
- `~/.config/sara/system-customizations.md` – curated state document SARA maintains automatically to track how the
  system deviates from a default installation. Its contents are injected into the system prompt at session start.

Example `.env` (`~/.config/sara/.env`):

```
# OpenRouter
SARA_BASE_URL=https://openrouter.ai/api/v1
SARA_MODEL=openai/gpt-4o
SARA_API_KEY=sk-or-...

# OpenAI
# SARA_BASE_URL=https://api.openai.com/v1
# SARA_MODEL=gpt-4o
# SARA_API_KEY=sk-...

# Optional Searxng (enables the web_search tool)
# SARA_SEARXNG_URL=http://localhost:8080
# SARA_SEARXNG_TOKEN=optional-bearer-token
```

You can also provide `~/.config/sara/.env.local` for local overrides; its entries take precedence over `.env`.

#### Running

Basic usage after configuration:

```
./sara
```

The interactive REPL reads input from stdin. Submit an empty line or press `Ctrl+D` (EOF) to end the session.

Common CLI parameters:

- `-m`, `--model <string>` – set/override the model
- `-v`, `--verbose` – enable verbose debug output
- `-b`, `--brave-mode` – skip confirmation prompts and execute tools automatically
- `--system-prompt-file <path>` – use a specific system prompt file (overrides the default
  `~/.config/sara/system-prompt.md`)

Examples:

```
./sara --model your-model-id
./sara -v
./sara -b
./sara --system-prompt-file /path/to/prompt.md
```

#### Tools

SARA exposes the following tools to the LLM:

- `exec_command` – run a local shell command (combined stdout/stderr). **unsafe** (always prompts unless brave mode is
  on).
- `read_file` – read a file by path. **safe** (no prompt). Guarded by a sensitive-data policy in the system prompt.
- `write_file` – write content to a file by path. **unsafe** (always prompts unless brave mode is on).
- `web_fetch` – fetch a web page and return it as Markdown, text, or HTML (always registered). **safe**.
- `web_search` – search the web via Searxng (only registered when `SARA_SEARXNG_URL` is set). **safe**.

Safe (read-only, side-effect-free) tools bypass the confirmation prompt even when brave mode is off. Unsafe tools always
prompt unless brave mode is on. When you decline a prompted tool, you may optionally provide a reason that is sent back
to the LLM.

#### Build from source (optional)

You can build the native executable with Gradle:

```
./gradlew build
```

The executable will be placed under:

```
build/bin/native/releaseExecutable/
```

Depending on the target, the binary may be named `sara` or `sara.kexe`.

#### Reference

- CLI flags:
    - `-m`, `--model <string>`
    - `-v`, `--verbose`
    - `-b`, `--brave-mode`
  - `--system-prompt-file <path>`
- Environment variables:
  - `SARA_MODEL` (required)
  - `SARA_API_KEY` (required)
  - `SARA_BASE_URL` (required)
  - `SARA_SEARXNG_URL` (optional)
  - `SARA_SEARXNG_TOKEN` (optional)
- .env locations:
    - `~/.config/sara/.env`
    - `~/.config/sara/.env.local`
    - Default system prompt file: `~/.config/sara/system-prompt.md`
  - Auto-maintained customizations file: `~/.config/sara/system-customizations.md`

#### License

See the LICENSE file for details.
