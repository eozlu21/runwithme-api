package com.runwithme.runwithme.api.mcp.prompts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.charset.StandardCharsets

object McpSchemaPrompts {
    private const val ANSWER_SCHEMA_RESOURCE = "/mcp/mcp-response-schema.json"
    private const val ANSWER_RULES_RESOURCE = "/mcp/mcp-schema-rules.txt"

    private val answerSchemaJsonText: String by lazy { loadResource(ANSWER_SCHEMA_RESOURCE) }
    private val answerSchemaMap: Map<String, Any> by lazy { loadSchemaMap() }
    private val answerRulesText: String by lazy { loadResource(ANSWER_RULES_RESOURCE) }

    fun answerSchemaJson(): String = answerSchemaJsonText

    fun answerSchemaMap(): Map<String, Any> = answerSchemaMap

    fun answerSchemaRules(): String = answerRulesText

    private fun loadResource(path: String): String {
        val stream =
            McpSchemaPrompts::class.java.getResourceAsStream(path)
                ?: throw IllegalStateException("Prompt resource not found: $path")
        stream.use {
            return it.readBytes().toString(StandardCharsets.UTF_8).trim()
        }
    }

    private fun loadSchemaMap(): Map<String, Any> =
        McpSchemaPrompts::class.java.getResourceAsStream(ANSWER_SCHEMA_RESOURCE)?.use { stream ->
            val text = stream.readBytes().toString(StandardCharsets.UTF_8)
            @Suppress("UNCHECKED_CAST")
            return@use jacksonObjectMapper().readValue(text, Map::class.java) as Map<String, Any>
        } ?: throw IllegalStateException("Prompt resource not found: $ANSWER_SCHEMA_RESOURCE")
}
