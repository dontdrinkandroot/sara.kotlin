package net.dontdrinkandroot.sara.extensions

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

fun Path.exists(): Boolean = SystemFileSystem.exists(this)
fun Path.isDirectory(): Boolean = SystemFileSystem.metadataOrNull(this)?.isDirectory ?: false
fun Path.appendFileName(fileName: String): Path = Path("${this.toString().removeSuffix("/")}/$fileName").also {
    require(this.isDirectory()) { "Path must be a directory" }
}

fun Path.isAbsolute(): Boolean = this.toString().startsWith("/")
fun Path.readString(): String = SystemFileSystem.source(this).buffered().use { it.readString() }
fun Path.readStringIfExists(): String? = when (exists()) {
    true -> readString()
    false -> null
}
