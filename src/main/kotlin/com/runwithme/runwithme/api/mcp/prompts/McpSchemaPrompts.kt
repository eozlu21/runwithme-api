package com.runwithme.runwithme.api.mcp.prompts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets

object McpSchemaPrompts {
    private const val ANSWER_SCHEMA_RESOURCE = "/mcp/mcp-response-schema.json"

    private val answerSchemaMap: Map<String, Any> by lazy { loadSchemaMap() }

    fun answerSchemaMap(): Map<String, Any> = answerSchemaMap

    private fun loadSchemaMap(): Map<String, Any> =
        McpSchemaPrompts::class.java.getResourceAsStream(ANSWER_SCHEMA_RESOURCE)?.use { stream ->
            val text = stream.readBytes().toString(StandardCharsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            return@use jacksonObjectMapper().readValue(text, Map::class.java) as Map<String, Any>
        } ?: throw IllegalStateException("Prompt resource not found: $ANSWER_SCHEMA_RESOURCE")
}
