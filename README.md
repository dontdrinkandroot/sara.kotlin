### Sara (Kotlin/Native)

A small, fast command-line assistant written in Kotlin/Native. It connects to an LLM API and provides a terminal-based
experience with optional tools and a configurable system prompt.

- Runs as a single native executable (no JVM required)
- Targets Linux (x64)

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

Configuration is resolved with the following precedence:

1. CLI parameters
2. Environment variables
3. .env files in `~/.config/sara/` (first `.env`, then `.env.local`)

Required values:

- `SARA_MODEL` – model identifier to use
- `SARA_API_KEY` – API key for the LLM provider
- `SARA_BASE_URL` – base URL of the LLM API (e.g., `https://openrouter.ai/api/v1`)

Optional values/files:

- System prompt file (used if present): `~/.config/sara/system-prompt.md`
- You can override the system prompt file path via `--system-prompt-file`

Using .env files (create `~/.config/sara/.env` and optionally `~/.config/sara/.env.local`):

```
SARA_MODEL=your-model-id
SARA_API_KEY=your-api-key
SARA_BASE_URL=https://openrouter.ai/api/v1
```

Notes:

- `.env.local` overrides `.env` entries
- Real environment variables override both `.env` files
- CLI flags override everything

#### Running

Basic usage after configuration:

```
./sara
```

Common CLI parameters:

- `-m`, `--model <string>` – set/override the model
- `-s`, `--websearch` – enable web search
- `-v`, `--verbose` – enable verbose output
- `--system-prompt-file <path>` – use a specific system prompt file
- `-b`, `--brave-mode` – skip confirmation for tool execution

Examples:

```
./sara --model your-model-id
./sara -v
./sara -s
./sara -b
./sara --system-prompt-file /path/to/prompt.md
```

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
    - `-s`, `--websearch`
    - `-v`, `--verbose`
    - `--system-prompt-file <path>`
    - `-b`, `--brave-mode`
- Environment variables:
    - `SARA_MODEL`
    - `SARA_API_KEY`
    - `SARA_BASE_URL`
- .env locations:
    - `~/.config/sara/.env`
    - `~/.config/sara/.env.local`
    - Default system prompt file: `~/.config/sara/system-prompt.md`

#### License

See the LICENSE file for details.
