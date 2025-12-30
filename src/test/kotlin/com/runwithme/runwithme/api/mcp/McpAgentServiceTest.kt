package com.runwithme.runwithme.api.mcp

import com.runwithme.runwithme.api.entity.Message
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.service.MessageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class McpAgentServiceTest {
    private val externalApiClient = mockk<McpExternalApiClient>(relaxed = true)
    private val geminiClient = mockk<GeminiClient>(relaxed = true)
    private val promptRouter = mockk<McpPromptRouter>(relaxed = true)
    private val messageService = mockk<MessageService>(relaxed = true)
    private val messageRepository = mockk<MessageRepository>(relaxed = true)
    private val properties =
        McpProperties(
            geminiApiKey = "dummy",
            agentUsername = "mcp-agent",
            agentUserId = UUID.randomUUID().toString(),
        )

    @Test
    fun `reset history clears gemini chat sessions`() {
        val starterUserId = UUID.randomUUID()
        every { messageRepository.save(any<Message>()) } answers { firstArg() }

        val service = createService()

        service.resetHistory(starterUserId)

        verify(exactly = 1) { geminiClient.resetChatSessions(starterUserId) }
        verify(exactly = 1) { messageRepository.save(match { it.content?.startsWith("__MCP_HISTORY_RESET__") == true }) }
    }

    @Test
    fun `extractUserMessageFromEnvelope returns summary text`() {
        val service = createService()
        val llmMessage =
            """{"type":"info","data":{"summary":"You do not have any friend suggestions at this time."},"errors":[]}"""

        val result = service.extractUserMessageFromEnvelope(llmMessage)

        assertEquals("You do not have any friend suggestions at this time.", result)
    }

    @Test
    fun `extractUserMessageFromEnvelope returns null for invalid payload`() {
        val service = createService()

        val result = service.extractUserMessageFromEnvelope("not-json")

        assertNull(result)
    }

    private fun createService(): McpAgentService =
        McpAgentService(
            externalApiClient,
            geminiClient,
            promptRouter,
            messageService,
            messageRepository,
            properties,
        )
}
