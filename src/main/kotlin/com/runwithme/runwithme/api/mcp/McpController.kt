package com.runwithme.runwithme.api.mcp

import com.runwithme.runwithme.api.repository.UserRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/mcp")
class McpController(
    private val agentService: McpAgentService,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(McpController::class.java)

    // Exposes the agent over HTTP for quick manual tests.
    @PostMapping("/run")
    fun run(
        @Valid @RequestBody request: McpAgentRequest,
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        authentication: Authentication,
    ): ResponseEntity<McpAgentResponse> {
        logger.info("MCP request received from user='{}'", authentication.name)
        val starterUserId =
            userRepository
                .findByUsername(authentication.name)
                .orElseThrow { IllegalStateException("Authenticated user not found: ${authentication.name}") }
                .userId
                ?: throw IllegalStateException("User id missing for ${authentication.name}")
        val response = agentService.runAgent(request, authorization, starterUserId)
        return if (response.success) {
            logger.info("MCP request succeeded for user='{}' route='{}'", authentication.name, response.routeName)
            ResponseEntity.ok(response)
        } else {
            logger.warn(
                "MCP request failed for user='{}' route='{}' error='{}'",
                authentication.name,
                response.routeName,
                response.error,
            )
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
        }
    }
}
