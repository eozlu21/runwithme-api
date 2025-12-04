package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.RouteLike
import com.runwithme.runwithme.api.entity.RouteLikeId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RouteLikeRepository : JpaRepository<RouteLike, RouteLikeId> {
    fun findByIdRouteId(
        routeId: Long,
        pageable: Pageable,
    ): Page<RouteLike>

    fun findByIdUserId(
        userId: UUID,
        pageable: Pageable,
    ): Page<RouteLike>

    @Query("SELECT COUNT(rl) FROM RouteLike rl WHERE rl.id.routeId = :routeId")
    fun countByRouteId(routeId: Long): Long

    fun existsByIdRouteIdAndIdUserId(
        routeId: Long,
        userId: UUID,
    ): Boolean
}
