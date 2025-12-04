package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.RoutePoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RoutePointRepository : JpaRepository<RoutePoint, Long> {
    fun findByRouteIdOrderBySeqNoAsc(routeId: Long): List<RoutePoint>

    fun deleteByRouteId(routeId: Long)
}
