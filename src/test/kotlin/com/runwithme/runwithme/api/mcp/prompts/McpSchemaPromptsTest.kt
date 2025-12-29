package com.runwithme.runwithme.api.mcp.prompts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpSchemaPromptsTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `answer schema json is valid`() {
        val schema = McpSchemaPrompts.answerSchemaJson()
        val node = mapper.readTree(schema)
        assertEquals("object", node["type"].asText())
        assertNotNull(node["properties"]["data"])
        assertNotNull(node["properties"]["errors"])
    }

    @Test
    fun `answer schema rules mention top level constraints`() {
        val rules = McpSchemaPrompts.answerSchemaRules()
        assertTrue(rules.contains("top-level"), "rules should mention top-level constraints")
        assertTrue(rules.contains("errors"), "rules should mention errors array")
    }
}
