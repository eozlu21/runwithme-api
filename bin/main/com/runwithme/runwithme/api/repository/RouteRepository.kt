package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.Route
import org.locationtech.jts.geom.Point
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RouteRepository : JpaRepository<Route, Long> {
    fun findByIsPublicTrue(pageable: Pageable): Page<Route>

    fun findByDifficulty(
        difficulty: String,
        pageable: Pageable,
    ): Page<Route>

    @Query(
        value =
            """
        SELECT * FROM routes r 
        WHERE r.id != :routeId 
        AND r.is_public = true 
        AND r.path_geom IS NOT NULL
        AND ST_DWithin(ST_Centroid(r.path_geom::geometry)::geography, :centroid, :maxDistance) 
        ORDER BY ST_Distance(ST_Centroid(r.path_geom::geometry)::geography, :centroid) ASC
    """,
        nativeQuery = true,
    )
    fun findSimilarRoutes(
        routeId: Long,
        centroid: Point,
        maxDistance: Double,
        pageable: Pageable,
    ): List<Route>

    @Query(
        value =
            """
        SELECT * FROM routes r 
        WHERE r.is_public = true 
        AND r.start_point IS NOT NULL 
        AND ST_DWithin(r.start_point, :point, :distance) 
        ORDER BY ST_Distance(r.start_point, :point) ASC
    """,
        nativeQuery = true,
    )
    fun findNearbyRoutes(
        point: Point,
        distance: Double,
        pageable: Pageable,
    ): Page<Route>
}
