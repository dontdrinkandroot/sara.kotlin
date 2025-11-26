@file:OptIn(ExperimentalForeignApi::class)

package net.dontdrinkandroot.sara.configuration

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import net.dontdrinkandroot.sara.extensions.*
import platform.posix.EOF
import platform.posix.fclose
import platform.posix.fgetc
import platform.posix.fopen

// Note: Enumerating process environment via C's `environ` is
// not directly exposed in Kotlin/Native in a portable way.
// We therefore read from /proc/self/environ on Linux, which lists
// NUL-separated KEY=VALUE entries. If that file is unavailable,
// we fall back to an empty map (process env cannot be enumerated
// portably on all native targets without custom interop).

/**
 * Read environment variables with precedence: ENV > .env.local > .env.
 * If [dotEnvPath] is null, only the real process environment is returned.
 */
fun readEnv(dotEnvPath: Path?): Map<String, String> {
    val processEnv = readProcessEnv()

    if (dotEnvPath == null || !dotEnvPath.exists()) return processEnv
    if (!dotEnvPath.isDirectory()) error("Dotenv path is not a directory: $dotEnvPath")
    if (!dotEnvPath.isAbsolute()) error("Dotenv path is not absolute: $dotEnvPath")

    val envFile = readDotEnv(dotEnvPath.appendFileName(".env"))
    val envLocalFile = readDotEnv(dotEnvPath.appendFileName(".env.local"))

    return buildMap {
        putAll(envFile)
        putAll(envLocalFile)
        putAll(processEnv)
    }
}

public fun readDotEnv(path: Path): Map<String, String> =
    path.readStringIfExists()
        ?.lineSequence()
        ?.mapNotNull(String::parseDotEnvEntry)
        ?.toMap()
        ?: emptyMap()


private fun readProcessEnv(): Map<String, String> = buildMap {
    val file = fopen("/proc/self/environ", "rb") ?: return@buildMap

    try {
        val bytes = generateSequence { fgetc(file).takeIf { it != EOF } }
            .map { it.toByte() }
            .toList()
            .toByteArray()

        if (bytes.isEmpty()) return@buildMap

        bytes.decodeToString()
            .split('\u0000')
            .filter { it.isNotEmpty() }
            .mapNotNull { it.parseDotEnvEntry() }
            .forEach { (key, value) -> put(key, value) }
    } finally {
        fclose(file)
    }
}

private fun String.parseDotEnvEntry(): Pair<String, String>? =
    trim()
        .takeIf { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
        ?.let { line ->
            val key = line.substringBefore('=').trim()
            val value = line.substringAfter('=').trim().removeSurroundingQuotes()
            key.takeIf(String::isNotEmpty)?.to(value)
        }
