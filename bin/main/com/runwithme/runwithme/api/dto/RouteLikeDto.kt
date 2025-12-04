package com.runwithme.runwithme.api.dto

import com.runwithme.runwithme.api.entity.RouteLike
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Route like data transfer object")
data class RouteLikeDto(
    @Schema(description = "Route ID", example = "1")
    val routeId: Long,
    @Schema(description = "User ID", example = "1")
    val userId: Long,
    @Schema(description = "Created timestamp")
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun fromEntity(routeLike: RouteLike): RouteLikeDto =
            RouteLikeDto(
                routeId = routeLike.id.routeId,
                userId = routeLike.id.userId,
                createdAt = routeLike.createdAt,
            )
    }
}
