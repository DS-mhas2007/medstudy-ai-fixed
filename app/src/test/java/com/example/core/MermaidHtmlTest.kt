package com.example.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidHtmlTest {
    @Test
    fun diagramTextCannotInjectMarkup() {
        val page = MermaidHtml.page("graph TD\nA[\"</pre><script>alert(1)</script>\"] --> B")
        assertFalse(page.contains("<script>alert(1)"))
        assertTrue(page.contains("&lt;script&gt;alert(1)"))
    }

    @Test
    fun loadsTheBundledLibraryAndStrictMode() {
        val page = MermaidHtml.page("graph TD\nA --> B")
        assertTrue(page.contains("src=\"mermaid.min.js\""))
        assertTrue(page.contains("securityLevel: \"strict\""))
        assertFalse(page.contains("http://"))
        assertFalse(page.contains("https://"))
    }
}
