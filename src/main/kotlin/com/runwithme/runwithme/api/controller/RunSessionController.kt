package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.AddRunSessionPointsRequest
import com.runwithme.runwithme.api.dto.EndRunSessionRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RunSessionDto
import com.runwithme.runwithme.api.dto.RunSessionPointDto
import com.runwithme.runwithme.api.dto.StartRunSessionRequest
import com.runwithme.runwithme.api.dto.UpdateRunSessionRequest
import com.runwithme.runwithme.api.repository.UserRepository
import com.runwithme.runwithme.api.service.RunSessionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/run-sessions")
@Tag(name = "Run Sessions", description = "Run session tracking and management APIs")
class RunSessionController(
    private val runSessionService: RunSessionService,
    private val userRepository: UserRepository,
) {
    @GetMapping("/{id}")
    @Operation(summary = "Get run session by ID", description = "Retrieves a single run session with all tracked points")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved run session",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun getRunSessionById(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
    ): ResponseEntity<RunSessionDto> {
        val session = runSessionService.getRunSessionById(id)
        return if (session != null) {
            ResponseEntity.ok(session)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get run sessions by user", description = "Retrieves paginated run sessions for a specific user")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved run sessions",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getRunSessionsByUser(
        @Parameter(description = "User ID") @PathVariable userId: UUID,
        @Parameter(description = "Page number (0-indexed)", example = "0") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Number of items per page", example = "10") @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageResponse<RunSessionDto>> = ResponseEntity.ok(runSessionService.getRunSessionsByUser(userId, page, size))

    @GetMapping("/public")
    @Operation(summary = "Get public run sessions", description = "Retrieves paginated public run sessions")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved public run sessions",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getPublicRunSessions(
        @Parameter(description = "Page number (0-indexed)", example = "0") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Number of items per page", example = "10") @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageResponse<RunSessionDto>> = ResponseEntity.ok(runSessionService.getPublicRunSessions(page, size))

    @GetMapping("/route/{routeId}")
    @Operation(summary = "Get run sessions by route", description = "Retrieves paginated run sessions for a specific route")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved run sessions",
                content = [Content(schema = Schema(implementation = PageResponse::class))],
            ),
        ],
    )
    fun getRunSessionsByRoute(
        @Parameter(description = "Route ID", example = "1") @PathVariable routeId: Long,
        @Parameter(description = "Page number (0-indexed)", example = "0") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Number of items per page", example = "10") @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<PageResponse<RunSessionDto>> = ResponseEntity.ok(runSessionService.getRunSessionsByRoute(routeId, page, size))

    @GetMapping("/me/active")
    @Operation(summary = "Get current user's active sessions", description = "Retrieves all active (not ended) run sessions for the authenticated user")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved active sessions",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
        ],
    )
    fun getMyActiveSessions(authentication: Authentication): ResponseEntity<List<RunSessionDto>> {
        val user = userRepository.findByUsername(authentication.name).orElseThrow { RuntimeException("User not found") }
        val sessions = runSessionService.getActiveSessionsForUser(user.userId!!)
        return ResponseEntity.ok(sessions)
    }

    @PostMapping
    @Operation(summary = "Start a new run session", description = "Creates and starts a new run session for the authenticated user")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Run session started successfully",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
        ],
    )
    fun startRunSession(
        @RequestBody request: StartRunSessionRequest,
        authentication: Authentication,
    ): ResponseEntity<RunSessionDto> {
        val session = runSessionService.startRunSession(request, authentication.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    @PostMapping("/{id}/points")
    @Operation(
        summary = "Add points to run session",
        description = "Adds one or more GPS points to an active run session. Optimized for high-frequency live tracking (every 5-10 seconds).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Points added successfully",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Session already ended or invalid request"),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun addPoints(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
        @RequestBody request: AddRunSessionPointsRequest,
    ): ResponseEntity<RunSessionDto> =
        try {
            val session = runSessionService.addPoints(id, request)
            ResponseEntity.ok(session)
        } catch (e: RuntimeException) {
            if (e.message?.contains("not found") == true) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
        }

    @PostMapping("/{id}/point")
    @Operation(
        summary = "Add single point to run session",
        description = "Convenience endpoint to add a single GPS point. For batch updates, use POST /{id}/points instead.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Point added successfully",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Session already ended or invalid request"),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun addSinglePoint(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
        @RequestBody point: RunSessionPointDto,
    ): ResponseEntity<RunSessionDto> =
        try {
            val session = runSessionService.addSinglePoint(id, point)
            ResponseEntity.ok(session)
        } catch (e: RuntimeException) {
            if (e.message?.contains("not found") == true) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
        }

    @PostMapping("/{id}/end")
    @Operation(summary = "End run session", description = "Ends an active run session and computes final statistics (distance, pace, elevation gain)")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Run session ended successfully",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "400", description = "Session already ended"),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun endRunSession(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
        @RequestBody(required = false) request: EndRunSessionRequest?,
    ): ResponseEntity<RunSessionDto> =
        try {
            val session = runSessionService.endRunSession(id, request)
            ResponseEntity.ok(session)
        } catch (e: RuntimeException) {
            if (e.message?.contains("not found") == true) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).build()
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST).build()
            }
        }

    @PutMapping("/{id}")
    @Operation(summary = "Update run session", description = "Updates run session properties (visibility, route)")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Run session updated successfully",
                content = [Content(schema = Schema(implementation = RunSessionDto::class))],
            ),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun updateRunSession(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
        @RequestBody request: UpdateRunSessionRequest,
    ): ResponseEntity<RunSessionDto> {
        val session = runSessionService.updateRunSession(id, request)
        return if (session != null) {
            ResponseEntity.ok(session)
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete run session", description = "Deletes a run session and all associated points")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Run session deleted successfully"),
            ApiResponse(responseCode = "404", description = "Run session not found"),
        ],
    )
    fun deleteRunSession(
        @Parameter(description = "Run session ID", example = "1") @PathVariable id: Long,
    ): ResponseEntity<Void> {
        val deleted = runSessionService.deleteRunSession(id)
        return if (deleted) {
            ResponseEntity.status(HttpStatus.NO_CONTENT).build()
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }
}
