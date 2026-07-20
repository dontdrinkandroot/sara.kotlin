package net.dontdrinkandroot.sara.tool

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import kotlinx.io.files.Path
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.dontdrinkandroot.sara.customizations.SystemCustomizationsStore
import net.dontdrinkandroot.sara.extensions.exists
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.system
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertTrue

class CustomizationToolsTest {

    private fun <T> runBlockingNoSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun resumeWith(res: Result<T>) {
                result = res
            }
        })
        return result!!.getOrThrow()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun createTempDir(prefix: String): Path {
        val tmp = getenv("TMPDIR")?.toKString()?.takeIf(String::isNotEmpty) ?: "/tmp"
        var counter = 0
        while (true) {
            val candidate = Path("$tmp/$prefix-${counter++}")
            if (!candidate.exists()) {
                check(mkdir(candidate.toString(), 0x1FFu) == 0) { "mkdir failed for $candidate" }
                return candidate
            }
        }
    }

    private fun cleanup(path: Path) {
        system("rm -rf '${path.toString().replace("'", "'\\''")}'")
    }

    private fun successOutput(result: ToolResult): String {
        assertTrue(result is ToolResult.Success, "Expected Success, got $result")
        return result.output
    }

    private fun errorMessage(result: ToolResult): String {
        assertTrue(result is ToolResult.Error, "Expected Error, got $result")
        return result.message
    }

    private fun tools(dir: Path): Triple<AddCustomizationTool, RemoveCustomizationTool, ReplaceCustomizationTool> {
        val store = SystemCustomizationsStore(dir)
        return Triple(AddCustomizationTool(store), RemoveCustomizationTool(store), ReplaceCustomizationTool(store))
    }

    @Test
    fun testToolFlags() {
        val dir = createTempDir("sara-tools-flags")
        try {
            val (add, remove, replace) = tools(dir)
            listOf(add, remove, replace).forEach { tool ->
                assertTrue(tool.isSafe, "${tool.name} should be safe")
                assertTrue(!tool.availableInPlanMode, "${tool.name} should not be available in plan mode")
            }
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testAddReturnsIdAndFreshState() {
        val dir = createTempDir("sara-tools-add")
        try {
            val (add, _, _) = tools(dir)
            val output = successOutput(
                runBlockingNoSuspend {
                    add.execute(buildJsonObject {
                        put("section", JsonPrimitive("installedPackages"))
                        put("entry", JsonPrimitive("ripgrep"))
                    }, verbose = false)
                }
            )
            assertTrue(output.contains("Added entry [1] to 'Installed packages': ripgrep"), output)
            assertTrue(output.contains("### Installed packages\n- [1] ripgrep"), output)

            val duplicate = successOutput(
                runBlockingNoSuspend {
                    add.execute(buildJsonObject {
                        put("section", JsonPrimitive("installedPackages"))
                        put("entry", JsonPrimitive("ripgrep"))
                    }, verbose = false)
                }
            )
            assertTrue(duplicate.contains("already present"), duplicate)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testAddValidatesArguments() {
        val dir = createTempDir("sara-tools-add-invalid")
        try {
            val (add, _, _) = tools(dir)

            val missingSection = errorMessage(
                runBlockingNoSuspend { add.execute(buildJsonObject {}, verbose = false) }
            )
            assertTrue(missingSection.contains("section"), missingSection)

            val unknownSection = errorMessage(
                runBlockingNoSuspend {
                    add.execute(buildJsonObject {
                        put("section", JsonPrimitive("nonsense"))
                        put("entry", JsonPrimitive("x"))
                    }, verbose = false)
                }
            )
            assertTrue(unknownSection.contains("Unknown section"), unknownSection)
            assertTrue(unknownSection.contains("installedPackages"), unknownSection)

            val missingEntry = errorMessage(
                runBlockingNoSuspend {
                    add.execute(buildJsonObject {
                        put("section", JsonPrimitive("other"))
                    }, verbose = false)
                }
            )
            assertTrue(missingEntry.contains("entry"), missingEntry)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testRemoveByIdAndNotFound() {
        val dir = createTempDir("sara-tools-remove")
        try {
            val (add, remove, _) = tools(dir)
            runBlockingNoSuspend {
                add.execute(buildJsonObject {
                    put("section", JsonPrimitive("services"))
                    put("entry", JsonPrimitive("docker — enabled"))
                }, verbose = false)
            }

            val removed = successOutput(
                runBlockingNoSuspend {
                    remove.execute(buildJsonObject { put("id", JsonPrimitive(1)) }, verbose = false)
                }
            )
            assertTrue(removed.contains("Removed entry [1] from 'Services'"), removed)
            assertTrue(removed.contains("(none recorded yet)"), removed)

            val notFound = errorMessage(
                runBlockingNoSuspend {
                    remove.execute(buildJsonObject { put("id", JsonPrimitive(42)) }, verbose = false)
                }
            )
            assertTrue(notFound.contains("No customization entry with id 42"), notFound)

            val invalidId = errorMessage(
                runBlockingNoSuspend {
                    remove.execute(buildJsonObject { put("id", JsonPrimitive("abc")) }, verbose = false)
                }
            )
            assertTrue(invalidId.contains("id"), invalidId)
        } finally {
            cleanup(dir)
        }
    }

    @Test
    fun testReplaceKeepsIdAndRejectsDuplicates() {
        val dir = createTempDir("sara-tools-replace")
        try {
            val (add, _, replace) = tools(dir)
            runBlockingNoSuspend {
                add.execute(buildJsonObject {
                    put("section", JsonPrimitive("installedPackages"))
                    put("entry", JsonPrimitive("ripgrep"))
                }, verbose = false)
                add.execute(buildJsonObject {
                    put("section", JsonPrimitive("installedPackages"))
                    put("entry", JsonPrimitive("fd"))
                }, verbose = false)
            }

            val replaced = successOutput(
                runBlockingNoSuspend {
                    replace.execute(buildJsonObject {
                        put("id", JsonPrimitive(2))
                        put("entry", JsonPrimitive("fd-find"))
                    }, verbose = false)
                }
            )
            assertTrue(replaced.contains("Replaced entry [2]"), replaced)
            assertTrue(replaced.contains("- [2] fd-find"), replaced)

            val duplicate = errorMessage(
                runBlockingNoSuspend {
                    replace.execute(buildJsonObject {
                        put("id", JsonPrimitive(2))
                        put("entry", JsonPrimitive("ripgrep"))
                    }, verbose = false)
                }
            )
            assertTrue(duplicate.contains("identical entry already exists with id 1"), duplicate)

            val notFound = errorMessage(
                runBlockingNoSuspend {
                    replace.execute(buildJsonObject {
                        put("id", JsonPrimitive(99))
                        put("entry", JsonPrimitive("x"))
                    }, verbose = false)
                }
            )
            assertTrue(notFound.contains("No customization entry with id 99"), notFound)
        } finally {
            cleanup(dir)
        }
    }
}
