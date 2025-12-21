package com.runwithme.runwithme.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.runwithme.runwithme.api.entity.RunSession
import com.runwithme.runwithme.api.entity.RunSessionPoint
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Run session data transfer object")
data class RunSessionDto(
    @Schema(description = "Run session unique identifier", example = "1")
    val id: Long,
    @Schema(description = "User ID who owns this session")
    val userId: UUID?,
    @Schema(description = "Optional route ID being followed", example = "5")
    val routeId: Long?,
    @Schema(description = "Is session publicly visible", example = "true")
    @get:JsonProperty("isPublic")
    val isPublic: Boolean,
    @Schema(description = "Session start time")
    val startedAt: OffsetDateTime?,
    @Schema(description = "Session end time (null if active)")
    val endedAt: OffsetDateTime?,
    @Schema(description = "Total moving time in seconds", example = "1800")
    val movingTimeS: Int?,
    @Schema(description = "Elevation gain in meters", example = "150.5")
    val elevationGainM: Double?,
    @Schema(description = "Average pace in seconds per kilometer", example = "320.5")
    val avgPaceSecPerKm: Double?,
    @Schema(description = "Total distance in meters", example = "5500.0")
    val totalDistanceM: Double?,
    @Schema(description = "List of tracked points")
    val points: List<RunSessionPointDto>? = null,
    @Schema(description = "Creation timestamp")
    val createdAt: OffsetDateTime?,
    @Schema(description = "Whether the session is currently active")
    @get:JsonProperty("isActive")
    val isActive: Boolean,
) {
    companion object {
        fun fromEntity(
            session: RunSession,
            points: List<RunSessionPointDto>? = null,
        ): RunSessionDto =
            RunSessionDto(
                id = session.id!!,
                userId = session.userId,
                routeId = session.routeId,
                isPublic = session.isPublic,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                movingTimeS = session.movingTimeS,
                elevationGainM = session.elevationGainM,
                avgPaceSecPerKm = session.avgPaceSecPerKm,
                totalDistanceM = session.totalDistanceM,
                points = points,
                createdAt = session.createdAt,
                isActive = session.endedAt == null,
            )
    }
}

@Schema(description = "Run session point data")
data class RunSessionPointDto(
    @Schema(description = "Sequence number", example = "1")
    val seqNo: Int,
    @Schema(description = "Latitude", example = "40.7829")
    val latitude: Double,
    @Schema(description = "Longitude", example = "-73.9654")
    val longitude: Double,
    @Schema(description = "Elevation in meters", example = "50.5")
    val elevationM: Double? = null,
    @Schema(description = "Timestamp when this point was recorded")
    val recordedAt: OffsetDateTime? = null,
) {
    companion object {
        fun fromEntity(point: RunSessionPoint): RunSessionPointDto =
            RunSessionPointDto(
                seqNo = point.seqNo ?: 0,
                latitude = point.pointGeom?.y ?: 0.0,
                longitude = point.pointGeom?.x ?: 0.0,
                elevationM = point.elevationM,
                recordedAt = point.recordedAt,
            )
    }
}

@Schema(description = "Start run session request")
data class StartRunSessionRequest(
    @Schema(description = "Optional route ID to follow", example = "5")
    val routeId: Long? = null,
    @Schema(description = "Is session publicly visible", example = "false")
    @field:JsonProperty("isPublic")
    @param:JsonProperty("isPublic")
    val isPublic: Boolean = false,
    @Schema(description = "Optional initial point")
    val initialPoint: RunSessionPointDto? = null,
)

@Schema(description = "Add points to run session request")
data class AddRunSessionPointsRequest(
    @Schema(description = "List of points to add (can be single point or batch)")
    val points: List<RunSessionPointDto>,
)

@Schema(description = "End run session request")
data class EndRunSessionRequest(
    @Schema(description = "Optional final point")
    val finalPoint: RunSessionPointDto? = null,
    @Schema(description = "Update visibility on end", example = "true")
    @field:JsonProperty("isPublic")
    @param:JsonProperty("isPublic")
    val isPublic: Boolean? = null,
    @Schema(description = "Moving time in seconds (overrides auto-calculation)", example = "1800")
    val movingTimeS: Int? = null,
)

@Schema(description = "Update run session request")
data class UpdateRunSessionRequest(
    @Schema(description = "Update visibility", example = "true")
    @field:JsonProperty("isPublic")
    @param:JsonProperty("isPublic")
    val isPublic: Boolean? = null,
    @Schema(description = "Update route ID", example = "5")
    val routeId: Long? = null,
)
