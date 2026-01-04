package com.runwithme.runwithme.api.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.dto.MessageDto
import com.runwithme.runwithme.api.entity.Message
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.repository.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.http.HttpHeaders
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ExecutorChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.messaging.Message as SpringMessage

@Configuration
class McpChatMiddlewareConfiguration(
    private val interceptorProvider: ObjectProvider<McpChatInboundInterceptor>,
) : WebSocketMessageBrokerConfigurer {
    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        interceptorProvider.ifAvailable?.let { registration.interceptors(it) }
    }

    @Bean
    fun mcpChatAutoReplyExecutor(): TaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 2
            maxPoolSize = 4
            setQueueCapacity(200)
            setThreadNamePrefix("mcp-chat-")
            initialize()
        }
}

/**
 * "Middleware" that watches incoming chat messages and, when the recipient is the configured MCP agent user,
 * runs the MCP agent and sends an auto-reply back to the sender.
 */
@Component
class McpChatInboundInterceptor(
    private val handler: McpChatAutoReplyHandler,
) : ExecutorChannelInterceptor {
    override fun afterMessageHandled(
        message: SpringMessage<*>,
        channel: MessageChannel,
        messageHandler: MessageHandler,
        ex: Exception?,
    ) {
        if (ex != null) {
            return
        }
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java) ?: return
        if (accessor.command != StompCommand.SEND) {
            return
        }
        if (accessor.destination != "/app/chat") {
            return
        }

        val request = handler.extractCreateMessageRequest(message.payload) ?: return
        val senderUsername = accessor.user?.name ?: return
        val authorizationHeader =
            accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)
                ?: (accessor.sessionAttributes?.get(HttpHeaders.AUTHORIZATION) as? String)

        handler.scheduleAutoReply(
            senderUsername = senderUsername,
            request = request,
            authorizationHeader = authorizationHeader,
        )
    }
}

@Component
class McpChatAutoReplyHandler(
    private val properties: McpProperties,
    private val agentService: McpAgentService,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val messagingTemplateProvider: ObjectProvider<SimpMessagingTemplate>,
    @Qualifier("mcpChatAutoReplyExecutor")
    private val executor: TaskExecutor,
) {
    private val logger = LoggerFactory.getLogger(McpChatAutoReplyHandler::class.java)
    private val objectMapper = jacksonObjectMapper()

    fun extractCreateMessageRequest(payload: Any?): CreateMessageRequest? =
        when (payload) {
            is CreateMessageRequest -> payload
            is ByteArray -> parseCreateMessageRequest(payload.toString(StandardCharsets.UTF_8))
            is String -> parseCreateMessageRequest(payload)
            else -> null
        }

    fun scheduleAutoReply(
        senderUsername: String,
        request: CreateMessageRequest,
        authorizationHeader: String?,
    ) {
        val agentUserId = agentUserIdOrNull() ?: return
        val agentUsername = properties.agentUsername.trim()
        if (agentUsername.isBlank()) {
            return
        }
        if (senderUsername.equals(agentUsername, ignoreCase = true)) {
            return
        }
        if (request.recipientId != agentUserId) {
            return
        }
        val prompt = request.content.trim()
        if (prompt.isBlank()) {
            return
        }

        executor.execute {
            runCatching {
                runAutoReply(
                    senderUsername = senderUsername,
                    prompt = prompt,
                    authorizationHeader = authorizationHeader,
                    agentUsername = agentUsername,
                    agentUserId = agentUserId,
                )
            }.onFailure { ex ->
                logger.error("MCP auto-reply failed: ${ex.message}", ex)
            }
        }
    }

    private fun runAutoReply(
        senderUsername: String,
        prompt: String,
        authorizationHeader: String?,
        agentUsername: String,
        agentUserId: UUID,
    ) {
        val starterUserId =
            userRepository
                .findByUsername(senderUsername)
                .orElse(null)
                ?.userId
                ?: return

        val response =
            agentService.runAgent(
                request = McpAgentRequest(prompt = prompt),
                authorizationHeader = authorizationHeader,
                starterUserId = starterUserId,
                starterUsername = senderUsername,
                persistUserPrompt = false,
                persistAgentReply = false,
            )

        val replyText =
            response.userMessage
                ?: response.error
                ?: response.llmMessage
                ?: return

        if (replyText.isBlank()) {
            return
        }

        val saved =
            messageRepository.save(
                Message(
                    senderId = agentUserId,
                    recipientId = starterUserId,
                    content = replyText,
                ),
            )
        val dto =
            MessageDto.fromEntity(
                saved,
                senderUsername = agentUsername,
                recipientUsername = senderUsername,
            )
        messagingTemplateProvider.ifAvailable?.convertAndSendToUser(senderUsername, "/queue/messages", dto)
    }

    private fun agentUserIdOrNull(): UUID? {
        val raw = properties.agentUserId.trim()
        if (raw.isBlank()) {
            return null
        }
        return try {
            UUID.fromString(raw)
        } catch (ex: IllegalArgumentException) {
            logger.warn("Invalid mcp.agent-user-id: ${ex.message}")
            null
        }
    }

    private fun parseCreateMessageRequest(text: String): CreateMessageRequest? =
        try {
            objectMapper.readValue(text, CreateMessageRequest::class.java)
        } catch (ex: Exception) {
            null
        }
}

/**
 * Same idea as the WebSocket interceptor, but for the REST endpoint `POST /api/v1/chat/send`.
 * This stays inside the MCP package and doesn't require changes to `ChatController`.
 */
@Component
class McpChatRestAutoReplyFilter(
    private val handler: McpChatAutoReplyHandler,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = request.method != "POST" || request.requestURI != "/api/v1/chat/send"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val cachingRequest = request as? ContentCachingRequestWrapper ?: ContentCachingRequestWrapper(request)
        val cachingResponse = response as? ContentCachingResponseWrapper ?: ContentCachingResponseWrapper(response)
        try {
            filterChain.doFilter(cachingRequest, cachingResponse)
        } finally {
            cachingResponse.copyBodyToResponse()
        }

        if (cachingResponse.status >= 400) {
            return
        }

        val auth = SecurityContextHolder.getContext().authentication ?: return
        val senderUsername = auth.name ?: return

        val body = cachingRequest.contentAsByteArray
        if (body.isEmpty()) {
            return
        }

        val requestBody = handler.extractCreateMessageRequest(body) ?: return
        val authorizationHeader = cachingRequest.getHeader(HttpHeaders.AUTHORIZATION)

        handler.scheduleAutoReply(
            senderUsername = senderUsername,
            request = requestBody,
            authorizationHeader = authorizationHeader,
        )
    }
}
