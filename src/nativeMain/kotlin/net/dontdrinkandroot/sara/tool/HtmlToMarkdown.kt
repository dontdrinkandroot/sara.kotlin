package net.dontdrinkandroot.sara.tool

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

/**
 * Converts HTML to Markdown by parsing the document with Ksoup and walking the DOM tree.
 *
 * Noise elements (script, style, nav, header, footer, aside, noscript, svg, form, iframe) are stripped
 * before conversion. Relative links and image sources are resolved against the document's base URI.
 */
object HtmlToMarkdown {

    private val noiseTags = setOf(
        "script", "style", "nav", "header", "footer", "aside", "noscript", "svg",
        "form", "iframe", "button", "input", "select", "canvas", "template",
    )

    /**
     * Parses [html] and converts it to Markdown.
     *
     * @param html The HTML string to convert.
     * @param baseUri The base URI for resolving relative URLs (e.g. the page URL).
     * @return The Markdown representation of the page content.
     */
    fun convert(html: String, baseUri: String = ""): String {
        val doc = Ksoup.parse(html, baseUri)
        return convertDocument(doc)
    }

    /**
     * Extracts the page title from [html].
     */
    fun extractTitle(html: String): String {
        val doc = Ksoup.parse(html)
        return doc.title().ifBlank { doc.body().text().take(100) }
    }

    /**
     * Converts a parsed [Document] to Markdown, stripping noise elements first.
     */
    fun convertDocument(doc: Document): String {
        for (tag in noiseTags) {
            doc.getElementsByTag(tag).forEach { it.remove() }
        }
        val body = doc.body()
        val sb = StringBuilder()
        convertChildren(body, sb)
        return postProcess(sb.toString())
    }

    private fun convertChildren(element: Element, sb: StringBuilder) {
        for (child in element.childNodes) {
            when (child) {
                is TextNode -> {
                    val text = child.text()
                    if (text.isNotEmpty()) sb.append(text)
                }

                is Element -> convertElement(child, sb)
                else -> {}
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun convertElement(element: Element, sb: StringBuilder) {
        when (element.normalName()) {
            "h1" -> heading(element, 1, sb)
            "h2" -> heading(element, 2, sb)
            "h3" -> heading(element, 3, sb)
            "h4" -> heading(element, 4, sb)
            "h5" -> heading(element, 5, sb)
            "h6" -> heading(element, 6, sb)
            "p" -> {
                sb.append("\n\n")
                convertChildren(element, sb)
                sb.append("\n\n")
            }

            "br" -> sb.append("\n")
            "hr" -> sb.append("\n\n---\n\n")
            "a" -> {
                val text = collectInlineText(element)
                val href = element.absUrl("href")
                if (href.isNotEmpty() && text.isNotEmpty()) {
                    sb.append("[").append(text).append("](").append(href).append(")")
                } else {
                    sb.append(text)
                }
            }

            "strong", "b" -> {
                sb.append("**")
                convertChildren(element, sb)
                sb.append("**")
            }

            "em", "i" -> {
                sb.append("*")
                convertChildren(element, sb)
                sb.append("*")
            }

            "code" -> {
                if (isInsidePre(element)) {
                    convertChildren(element, sb)
                } else {
                    sb.append("`")
                    convertChildren(element, sb)
                    sb.append("`")
                }
            }

            "pre" -> {
                sb.append("\n\n```\n")
                convertChildren(element, sb)
                sb.append("\n```\n\n")
            }

            "blockquote" -> {
                sb.append("\n")
                val inner = StringBuilder()
                convertChildren(element, inner)
                for (line in inner.toString().trim().lines()) {
                    sb.append("> ").append(line).append("\n")
                }
                sb.append("\n")
            }

            "ul" -> convertList(element, sb, ordered = false, depth = 0)
            "ol" -> convertList(element, sb, ordered = true, depth = 0)
            "li" -> convertChildren(element, sb)
            "img" -> {
                val alt = element.attr("alt")
                val src = element.absUrl("src")
                if (src.isNotEmpty()) {
                    sb.append("![").append(alt).append("](").append(src).append(")")
                }
            }

            "table" -> convertTable(element, sb)
            "title", "head" -> {}
            "html", "body" -> convertChildren(element, sb)
            else -> convertChildren(element, sb)
        }
    }

    private fun heading(element: Element, level: Int, sb: StringBuilder) {
        sb.append("\n\n")
        sb.append("#".repeat(level))
        sb.append(" ")
        convertChildren(element, sb)
        sb.append("\n\n")
    }

    private fun convertList(element: Element, sb: StringBuilder, ordered: Boolean, depth: Int) {
        val indent = "  ".repeat(depth)
        var index = 1
        for (child in element.children()) {
            if (child.normalName() == "li") {
                val marker = if (ordered) "${index}. " else "- "
                sb.append(indent).append(marker)
                val itemSb = StringBuilder()
                convertListItem(child, itemSb)
                sb.append(itemSb.toString().trim())
                sb.append("\n")
                val nestedLists = child.children().filter { it.normalName() in setOf("ul", "ol") }
                for (nested in nestedLists) {
                    convertList(nested, sb, ordered = nested.normalName() == "ol", depth = depth + 1)
                }
                index++
            }
        }
        if (depth == 0) sb.append("\n")
    }

    private fun convertListItem(element: Element, sb: StringBuilder) {
        for (child in element.childNodes) {
            when (child) {
                is TextNode -> {
                    val text = child.text()
                    if (text.isNotEmpty()) sb.append(text)
                }

                is Element -> {
                    if (child.normalName() !in setOf("ul", "ol")) {
                        convertElement(child, sb)
                    }
                }

                else -> {}
            }
        }
    }

    private fun convertTable(element: Element, sb: StringBuilder) {
        val rows = element.select("tr")
        if (rows.isEmpty()) {
            convertChildren(element, sb)
            return
        }

        sb.append("\n\n")
        val headerCells = rows.firstOrNull()?.select("th, td") ?: emptyList()
        if (headerCells.isNotEmpty()) {
            sb.append("| ")
            sb.append(headerCells.joinToString(" | ") { it.text() })
            sb.append(" |\n")
            sb.append("|")
            sb.append(headerCells.joinToString("") { "---|" })
            sb.append("\n")
        }

        val dataRows = if (headerCells.isNotEmpty()) rows.drop(1) else rows
        for (row in dataRows) {
            val cells = row.select("th, td")
            if (cells.isEmpty()) continue
            sb.append("| ")
            sb.append(cells.joinToString(" | ") { it.text().replace("\n", " ") })
            sb.append(" |\n")
        }
        sb.append("\n")
    }

    private fun collectInlineText(element: Element): String {
        val sb = StringBuilder()
        convertChildren(element, sb)
        return sb.toString().trim()
    }

    private fun isInsidePre(element: Element): Boolean {
        var parent: Node? = element.parent()
        while (parent != null) {
            if (parent is Element && parent.normalName() == "pre") return true
            parent = parent.parent()
        }
        return false
    }

    private fun postProcess(markdown: String): String {
        return markdown
            .replace(Regex("[ \\t]+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
