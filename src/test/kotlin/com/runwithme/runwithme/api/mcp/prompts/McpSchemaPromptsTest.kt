package com.runwithme.runwithme.api.mcp.prompts

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class McpSchemaPromptsTest {
    @Test
    fun `answer schema map is valid`() {
        val schema = McpSchemaPrompts.answerSchemaMap()
        assertTrue(schema["type"] == "object", "schema type should be object")
        @Suppress("UNCHECKED_CAST")
        val properties = schema["properties"] as? Map<String, Any>
        assertNotNull(properties, "schema should have properties")
        assertNotNull(properties?.get("data"), "schema should have data property")
        assertNotNull(properties?.get("errors"), "schema should have errors property")
    }
}
