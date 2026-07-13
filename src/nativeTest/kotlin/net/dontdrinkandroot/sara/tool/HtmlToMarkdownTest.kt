package net.dontdrinkandroot.sara.tool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlToMarkdownTest {

    @Test
    fun testConvertSimpleParagraph() {
        val html = "<html><body><p>Hello World</p></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertEquals("Hello World", markdown)
    }

    @Test
    fun testConvertHeadings() {
        val html = "<html><body><h1>Title</h1><h2>Subtitle</h2><h3>Section</h3></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("# Title"))
        assertTrue(markdown.contains("## Subtitle"))
        assertTrue(markdown.contains("### Section"))
    }

    @Test
    fun testConvertLinks() {
        val html = """<html><body><a href="https://example.com/path">Click here</a></body></html>"""
        val markdown = HtmlToMarkdown.convert(html, "https://example.com")
        assertTrue(markdown.contains("[Click here](https://example.com/path)"))
    }

    @Test
    fun testConvertRelativeLinkWithBaseUri() {
        val html = """<html><body><a href="/docs/guide">Guide</a></body></html>"""
        val markdown = HtmlToMarkdown.convert(html, "https://example.com")
        assertTrue(markdown.contains("[Guide](https://example.com/docs/guide)"))
    }

    @Test
    fun testConvertStrongAndEm() {
        val html = "<html><body><p><strong>bold</strong> and <em>italic</em></p></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("**bold**"))
        assertTrue(markdown.contains("*italic*"))
    }

    @Test
    fun testConvertUnorderedList() {
        val html = "<html><body><ul><li>Apple</li><li>Banana</li><li>Cherry</li></ul></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("- Apple"))
        assertTrue(markdown.contains("- Banana"))
        assertTrue(markdown.contains("- Cherry"))
    }

    @Test
    fun testConvertOrderedList() {
        val html = "<html><body><ol><li>First</li><li>Second</li><li>Third</li></ol></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("1. First"))
        assertTrue(markdown.contains("2. Second"))
        assertTrue(markdown.contains("3. Third"))
    }

    @Test
    fun testConvertNestedList() {
        val html = """
            <html><body>
            <ul>
                <li>Top level
                    <ul>
                        <li>Nested item</li>
                    </ul>
                </li>
            </ul>
            </body></html>
        """.trimIndent()
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("- Top level"))
        assertTrue(markdown.contains("  - Nested item"))
    }

    @Test
    fun testConvertCodeBlock() {
        val html = "<html><body><pre><code>val x = 42</code></pre></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("```"))
        assertTrue(markdown.contains("val x = 42"))
    }

    @Test
    fun testConvertInlineCode() {
        val html = "<html><body><p>Use <code>kotlin.test</code> for testing</p></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("`kotlin.test`"))
    }

    @Test
    fun testConvertBlockquote() {
        val html = "<html><body><blockquote>To be or not to be</blockquote></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("> To be or not to be"))
    }

    @Test
    fun testConvertTable() {
        val html = """
            <html><body>
            <table>
                <tr><th>Name</th><th>Age</th></tr>
                <tr><td>Alice</td><td>30</td></tr>
                <tr><td>Bob</td><td>25</td></tr>
            </table>
            </body></html>
        """.trimIndent()
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("| Name | Age |"))
        assertTrue(markdown.contains("|---|---|"))
        assertTrue(markdown.contains("| Alice | 30 |"))
        assertTrue(markdown.contains("| Bob | 25 |"))
    }

    @Test
    fun testStripScriptAndStyle() {
        val html =
            "<html><head><style>body { color: red; }</style></head><body><script>alert('xss')</script><p>Content</p></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("Content"))
        assertTrue(!markdown.contains("alert"))
        assertTrue(!markdown.contains("color"))
    }

    @Test
    fun testStripNavAndFooter() {
        val html = """
            <html><body>
            <nav><a href="/home">Home</a></nav>
            <main><p>Main content</p></main>
            <footer>Copyright 2024</footer>
            </body></html>
        """.trimIndent()
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("Main content"))
        assertTrue(!markdown.contains("Home"))
        assertTrue(!markdown.contains("Copyright"))
    }

    @Test
    fun testConvertHr() {
        val html = "<html><body><p>Before</p><hr><p>After</p></body></html>"
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("---"))
    }

    @Test
    fun testConvertImage() {
        val html = """<html><body><img src="https://example.com/img.png" alt="Logo"></body></html>"""
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(markdown.contains("![Logo](https://example.com/img.png)"))
    }

    @Test
    fun testExtractTitle() {
        val html = "<html><head><title>My Page Title</title></head><body><p>Content</p></body></html>"
        assertEquals("My Page Title", HtmlToMarkdown.extractTitle(html))
    }

    @Test
    fun testExtractTitleFallback() {
        val html = "<html><body><p>No title here but some content that is long enough</p></body></html>"
        val title = HtmlToMarkdown.extractTitle(html)
        assertTrue(title.isNotBlank())
        assertTrue(title.contains("No title here"))
    }

    @Test
    fun testPostProcessingCollapsesExtraNewlines() {
        val html = """
            <html><body>
            <p>First</p>
            <p>Second</p>
            <p>Third</p>
            </body></html>
        """.trimIndent()
        val markdown = HtmlToMarkdown.convert(html)
        assertTrue(!markdown.contains("\n\n\n"))
    }
}
