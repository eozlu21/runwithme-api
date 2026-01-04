package com.runwithme.runwithme.api.mcp

import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Component
class McpLogger(
    properties: McpProperties,
) {
    private val logFilePath: Path = Paths.get(properties.mcpLogFile)
    private val lock = ReentrantLock()
    private val maxPayloadChars = 2_000
    private val maxMetadataValueChars = 200

    init {
        val parent = logFilePath.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        if (!Files.exists(logFilePath)) {
            Files.createFile(logFilePath)
        }
    }

    fun logGeminiRequest(
        stage: GeminiLogStage,
        payload: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val prefix = buildPrefix(stage, "GeminiRequest", metadata)
        val body = payload.ifEmpty { "<empty payload>" }.let { truncate(it) }
        writeEntry(prefix, body)
    }

    fun logGeminiResponse(
        stage: GeminiLogStage,
        payload: String?,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        val prefix = buildPrefix(stage, "GeminiResponse", metadata)
        writeEntry(prefix, payload?.let { truncate(it) } ?: "<null payload>")
    }

    fun logGeminiError(
        stage: GeminiLogStage,
        message: String,
        metadata: Map<String, Any?> = emptyMap(),
        throwable: Throwable? = null,
    ) {
        val errorLine = "${buildPrefix(stage, "GeminiError", metadata)} message=$message"
        writeEntry(errorLine, throwable?.stackTraceToString()?.let { truncate(it) })
    }

    fun logEvent(
        event: String,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        writeEntry(buildPrefix(null, event, metadata), "")
    }

    fun logExternalApiRequest(
        routeName: String,
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ) {
        val metadata =
            linkedMapOf<String, Any?>(
                "routeName" to routeName,
                "method" to method,
                "url" to url,
                "bodyLength" to body?.length,
            )
        val headerLine = buildPrefix(null, "ExternalApiRequest", metadata)
        val payload =
            buildString {
                if (headers.isNotEmpty()) {
                    appendLine("headers=${headers.entries.joinToString { "${it.key}=${it.value}" }}")
                }
                if (!body.isNullOrBlank()) {
                    append("body=").append(truncate(body))
                }
            }
        writeEntry(headerLine, payload)
    }

    fun logExternalApiResponse(
        routeName: String,
        method: String,
        url: String,
        statusCode: Int,
        body: String?,
    ) {
        val metadata =
            linkedMapOf<String, Any?>(
                "routeName" to routeName,
                "method" to method,
                "url" to url,
                "statusCode" to statusCode,
                "bodyLength" to body?.length,
            )
        val headerLine = buildPrefix(null, "ExternalApiResponse", metadata)
        writeEntry(headerLine, body?.let { truncate(it) } ?: "<null body>")
    }

    fun logExternalApiError(
        routeName: String,
        method: String,
        url: String,
        statusCode: Int?,
        errorMessage: String,
        responseBody: String? = null,
        throwable: Throwable? = null,
    ) {
        val metadata =
            linkedMapOf<String, Any?>(
                "routeName" to routeName,
                "method" to method,
                "url" to url,
                "statusCode" to statusCode,
            )
        val headerLine = "${buildPrefix(null, "ExternalApiError", metadata)} message=$errorMessage"
        val payload =
            buildString {
                if (!responseBody.isNullOrBlank()) {
                    appendLine("responseBody=${truncate(responseBody)}")
                }
                if (throwable != null) {
                    append(throwable.stackTraceToString())
                }
            }
        writeEntry(headerLine, payload)
    }

    private fun buildPrefix(
        stage: GeminiLogStage?,
        label: String,
        metadata: Map<String, Any?>,
    ): String {
        val builder = StringBuilder()
        builder.append(Instant.now()).append(" MCP|").append(label)
        if (stage != null) {
            builder.append("|stage=").append(stage.name)
        }
        if (metadata.isNotEmpty()) {
            builder.append("|meta=").append(metadata.entries.joinToString { "${it.key}=${sanitizeMetadataValue(it.value)}" })
        }
        return builder.toString()
    }

    private fun writeEntry(
        header: String,
        payload: String?,
    ) {
        val sanitizedPayload = payload?.takeIf { it.isNotEmpty() } ?: ""
        val text =
            buildString {
                append(header)
                append(System.lineSeparator())
                if (sanitizedPayload.isNotEmpty()) {
                    append(sanitizedPayload)
                    append(System.lineSeparator())
                }
            }
        lock.withLock {
            Files.writeString(
                logFilePath,
                text,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    private fun truncate(value: String): String =
        if (value.length <= maxPayloadChars) {
            value
        } else {
            value.take(maxPayloadChars) + "...<truncated>"
        }

    private fun sanitizeMetadataValue(value: Any?): String {
        val raw = value?.toString() ?: "null"
        val singleLine = raw.replace("\r", "\\r").replace("\n", "\\n")
        return if (singleLine.length <= maxMetadataValueChars) {
            singleLine
        } else {
            singleLine.take(maxMetadataValueChars) + "...<truncated>"
        }
    }
}
