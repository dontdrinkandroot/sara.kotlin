package net.dontdrinkandroot.sara.systemprompt.systeminformation.general

import net.dontdrinkandroot.sara.systemprompt.SystemPromptProvider
import net.dontdrinkandroot.sara.systemprompt.cmd

/**
 * Probes the host for a known package manager and emits `Package manager: <name>`.
 * Saves the agent a reasoning step (and wrong guesses on derivatives) when it needs to
 * install or query packages.
 */
class PackageManagerProvider : SystemPromptProvider {
    override fun provide(): String? {
        val probe = cmd(
            candidates.joinToString(separator = "; ") { pm ->
                "command -v $pm >/dev/null 2>&1 && echo $pm"
            }
        )
        return detectPackageManager(probe)
    }

    private companion object {
        // Order is the priority: the first match wins.
        val candidates = listOf("apt", "dnf", "yum", "pacman", "zypper", "apk", "emerge", "nix", "rpm-ostree")
    }
}

/**
 * Picks the highest-priority package manager from the probe output (which lists every
 * candidate found on its own line) and formats it as `Package manager: <name>`.
 * Returns null when no known manager is available.
 */
internal fun detectPackageManager(probeOutput: String?): String? {
    val available = probeOutput?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
    val chosen = PackageManagerPriority.firstOrNull { it in available }
    return chosen?.let { "Package manager: $it" }
}

private val PackageManagerPriority = listOf(
    "apt", "dnf", "yum", "pacman", "zypper", "apk", "emerge", "nix", "rpm-ostree"
)
