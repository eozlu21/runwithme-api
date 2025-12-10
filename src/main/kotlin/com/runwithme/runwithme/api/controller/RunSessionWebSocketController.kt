package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.RunSessionDto
import com.runwithme.runwithme.api.dto.RunSessionPointDto
import com.runwithme.runwithme.api.service.RunSessionService
import org.slf4j.LoggerFactory
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller

/**
 * WebSocket controller for real-time run session tracking.
 * Uses the same RunSessionService as the REST controller for consistency.
 *
 * Client usage:
 * 1. Connect to /ws endpoint with JWT in Authorization header
 * 2. Subscribe to /topic/run-sessions/{sessionId} for updates
 * 3. Send points to /app/run-sessions/{sessionId}/track
 */
@Controller
class RunSessionWebSocketController(
    private val runSessionService: RunSessionService,
    private val messagingTemplate: SimpMessagingTemplate,
) {
    private val logger = LoggerFactory.getLogger(RunSessionWebSocketController::class.java)

    /**
     * Receives a single GPS point and broadcasts the updated session to all subscribers.
     * Optimized for high-frequency live tracking (every 5-10 seconds).
     *
     * @param sessionId The run session ID
     * @param point The GPS point to add
     * @return The updated run session (also broadcast to /topic/run-sessions/{sessionId})
     */
    @MessageMapping("/run-sessions/{sessionId}/track")
    @SendTo("/topic/run-sessions/{sessionId}")
    fun trackPoint(
        @DestinationVariable sessionId: Long,
        point: RunSessionPointDto,
    ): RunSessionDto? =
        try {
            logger.debug("WebSocket track point for session $sessionId: lat=${point.latitude}, lon=${point.longitude}")
            val session = runSessionService.addSinglePoint(sessionId, point)
            session
        } catch (e: Exception) {
            logger.error("Error tracking point for session $sessionId: ${e.message}")
            null
        }

    /**
     * Broadcasts a run session update to all subscribers.
     * Can be called from REST controller or other services to notify WebSocket clients.
     */
    fun broadcastSessionUpdate(session: RunSessionDto) {
        messagingTemplate.convertAndSend("/topic/run-sessions/${session.id}", session)
    }

    /**
     * Broadcasts session end event to all subscribers.
     */
    fun broadcastSessionEnded(session: RunSessionDto) {
        messagingTemplate.convertAndSend("/topic/run-sessions/${session.id}/ended", session)
    }
}
