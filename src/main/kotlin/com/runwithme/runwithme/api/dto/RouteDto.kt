package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.Route
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Route data transfer object")
data class RouteDto(
    @Schema(description = "Route unique identifier", example = "1") val id: Long,
    @Schema(description = "Route title", example = "Morning Run in Central Park")
    val title: String?,
    @Schema(description = "Route description", example = "A scenic route through the park")
    val description: String?,
    @Schema(description = "Distance in meters", example = "5500") val distanceM: Double?,
    @Schema(description = "Estimated duration in seconds", example = "1800")
    val estimatedDurationS: Int?,
    @Schema(description = "Difficulty level", example = "intermediate") val difficulty: String?,
    @Schema(description = "Is route publicly visible", example = "true") val isPublic: Boolean,
    @Schema(description = "Start point latitude", example = "40.7829")
    val startPointLat: Double?,
    @Schema(description = "Start point longitude", example = "-73.9654")
    val startPointLon: Double?,
    @Schema(description = "End point latitude", example = "40.7850") val endPointLat: Double?,
    @Schema(description = "End point longitude", example = "-73.9680") val endPointLon: Double?,
    @Schema(description = "List of route points") val points: List<RoutePointDto>? = null,
    @Schema(description = "ID of the user who created the route", example = "1")
    val creatorId: Long?,
    @Schema(description = "Creation timestamp") val createdAt: OffsetDateTime,
    @Schema(description = "Last update timestamp") val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(
            route: Route,
            points: List<RoutePointDto>? = null,
        ): RouteDto =
            RouteDto(
                id = route.id!!,
                title = route.title,
                description = route.description,
                distanceM = route.distanceM,
                estimatedDurationS = route.estimatedDurationS,
                difficulty = route.difficulty,
                isPublic = route.isPublic,
                startPointLat = route.startPoint?.y,
                startPointLon = route.startPoint?.x,
                endPointLat = route.endPoint?.y,
                endPointLon = route.endPoint?.x,
                points = points,
                creatorId = route.creatorId,
                createdAt = route.createdAt,
                updatedAt = route.updatedAt,
            )
    }
}

@Schema(description = "Route creation request")
data class CreateRouteRequest(
    @Schema(
        description = "Route title",
        example = "Morning Run in Central Park",
        required = true,
    )
    val title: String,
    @Schema(description = "Route description", example = "A scenic route through the park")
    val description: String? = null,
    @Schema(description = "Distance in meters", example = "5500") val distanceM: Double? = null,
    @Schema(description = "Estimated duration in seconds", example = "1800")
    val estimatedDurationS: Int? = null,
    @Schema(description = "Difficulty level", example = "intermediate")
    val difficulty: String? = null,
    @Schema(description = "Is route publicly visible", example = "true")
    val isPublic: Boolean = true,
    @Schema(description = "Start point latitude", example = "40.7829")
    val startPointLat: Double? = null,
    @Schema(description = "Start point longitude", example = "-73.9654")
    val startPointLon: Double? = null,
    @Schema(description = "End point latitude", example = "40.7850")
    val endPointLat: Double? = null,
    @Schema(description = "End point longitude", example = "-73.9680")
    val endPointLon: Double? = null,
    @Schema(description = "List of route points") val points: List<RoutePointDto>? = null,
)

@Schema(description = "Route update request")
data class UpdateRouteRequest(
    @Schema(description = "Route title", example = "Morning Run in Central Park")
    val title: String? = null,
    @Schema(description = "Route description", example = "A scenic route through the park")
    val description: String? = null,
    @Schema(description = "Distance in meters", example = "5500") val distanceM: Double? = null,
    @Schema(description = "Estimated duration in seconds", example = "1800")
    val estimatedDurationS: Int? = null,
    @Schema(description = "Difficulty level", example = "intermediate")
    val difficulty: String? = null,
    @Schema(description = "Is route publicly visible", example = "true")
    val isPublic: Boolean? = null,
    @Schema(description = "Start point latitude", example = "40.7829")
    val startPointLat: Double? = null,
    @Schema(description = "Start point longitude", example = "-73.9654")
    val startPointLon: Double? = null,
    @Schema(description = "End point latitude", example = "40.7850")
    val endPointLat: Double? = null,
    @Schema(description = "End point longitude", example = "-73.9680")
    val endPointLon: Double? = null,
    @Schema(description = "List of route points") val points: List<RoutePointDto>? = null,
)

@Schema(description = "Route point data")
data class RoutePointDto(
    @Schema(description = "Sequence number", example = "1") val seqNo: Int,
    @Schema(description = "Latitude", example = "40.7829") val latitude: Double,
    @Schema(description = "Longitude", example = "-73.9654") val longitude: Double,
    @Schema(description = "Elevation in meters", example = "50.5")
    val elevationM: Double? = null,
)
