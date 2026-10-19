package com.example.core

import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MermaidBuilderTest {
    private val nodes = listOf(
        DiagramNode("A", "Stimulus (ACh)", "المحفز", "mechanism"),
        DiagramNode("B", "Say \"hi\"", "", "warning"),
        DiagramNode("C", "Result", "", "weird-category")
    )

    @Test fun buildsQuotedLabelsEdgesAndClasses() {
        val code = MermaidBuilder.fromNodes(nodes, listOf(DiagramEdge("A", "B", "causes"), DiagramEdge("B", "Z", "ghost")))
        assertTrue(code.startsWith("graph TD"))
        assertTrue(code.contains("A[\"Stimulus (ACh)\"]"))
        assertTrue(code.contains("B[\"Say #quot;hi#quot;\"]"))
        assertTrue(code.contains("A -->|\"causes\"| B"))
        assertFalse(code.contains("Z"))
        assertTrue(code.contains("class A,C mechanism"))
        assertTrue(code.contains("class B warning"))
    }

    @Test fun usesArabicLabelsWhenAsked() {
        assertTrue(MermaidBuilder.fromNodes(nodes, emptyList(), arabicLabels = true).contains("A[\"المحفز\"]"))
    }

    @Test fun tooFewNodesGivesEmpty() {
        assertEquals("", MermaidBuilder.fromNodes(nodes.take(1), emptyList()))
    }

    @Test fun sanitizeKeepsOnlyKnownDiagramTypes() {
        assertEquals("graph TD\nA-->B", MermaidBuilder.sanitizeModelCode("```mermaid\ngraph TD\nA-->B\n```".replace("mermaid\n", "\n")))
        assertEquals("", MermaidBuilder.sanitizeModelCode("<script>alert(1)</script>"))
        assertEquals("", MermaidBuilder.sanitizeModelCode(""))
    }

    @Test fun numericIdsAreMadeSafe() {
        val code = MermaidBuilder.fromNodes(listOf(DiagramNode("1", "a"), DiagramNode("2", "b")), listOf(DiagramEdge("1", "2")))
        assertTrue(code.contains("n1[\"a\"]"))
        assertTrue(code.contains("n1 --> n2"))
    }
}
