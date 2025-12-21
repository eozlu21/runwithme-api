package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateRouteRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RouteDto
import com.runwithme.runwithme.api.dto.RoutePointDto
import com.runwithme.runwithme.api.dto.UpdateRouteRequest
import com.runwithme.runwithme.api.entity.Route
import com.runwithme.runwithme.api.entity.RoutePoint
import com.runwithme.runwithme.api.repository.RoutePointRepository
import com.runwithme.runwithme.api.repository.RouteRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class RouteService(
    private val routeRepository: RouteRepository,
    private val routePointRepository: RoutePointRepository,
    private val routeMatchingService: RouteMatchingService,
    private val userRepository: UserRepository,
) {
    private val geometryFactory = GeometryFactory()

    fun getAllRoutes(
        page: Int,
        size: Int,
    ): PageResponse<RouteDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val routePage = routeRepository.findAll(pageRequest)
        return PageResponse.fromPage(routePage) { RouteDto.fromEntity(it) }
    }

    fun getPublicRoutes(
        page: Int,
        size: Int,
    ): PageResponse<RouteDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val routePage = routeRepository.findByIsPublicTrue(pageRequest)
        return PageResponse.fromPage(routePage) { RouteDto.fromEntity(it) }
    }

    fun getRoutesByDifficulty(
        difficulty: String,
        page: Int,
        size: Int,
    ): PageResponse<RouteDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val routePage = routeRepository.findByDifficulty(difficulty, pageRequest)
        return PageResponse.fromPage(routePage) { RouteDto.fromEntity(it) }
    }

    fun getRouteById(id: Long): RouteDto? {
        val route = routeRepository.findById(id).orElse(null) ?: return null
        val points =
            routePointRepository.findByRouteIdOrderBySeqNoAsc(id).map {
                RoutePointDto(
                    seqNo = it.seqNo ?: 0,
                    latitude = it.pointGeom?.y ?: 0.0,
                    longitude = it.pointGeom?.x ?: 0.0,
                    elevationM = it.elevationM,
                )
            }
        return RouteDto.fromEntity(route, points)
    }

    @Transactional
    fun createRoute(
        request: CreateRouteRequest,
        username: String,
    ): RouteDto {
        val user =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val startPoint =
            if (request.startPointLat != null && request.startPointLon != null) {
                geometryFactory
                    .createPoint(
                        Coordinate(request.startPointLon, request.startPointLat),
                    ).also { it.srid = 4326 }
            } else {
                null
            }

        val endPoint =
            if (request.endPointLat != null && request.endPointLon != null) {
                geometryFactory
                    .createPoint(
                        Coordinate(request.endPointLon, request.endPointLat),
                    ).also { it.srid = 4326 }
            } else {
                null
            }

        var route =
            Route(
                title = request.title,
                description = request.description,
                distanceM = request.distanceM,
                estimatedDurationS = request.estimatedDurationS,
                difficulty = request.difficulty,
                isPublic = request.isPublic,
                startPoint = startPoint,
                endPoint = endPoint,
                creatorId = user.userId,
            )
        route = routeRepository.save(route)

        val pointsDto = request.points
        if (!pointsDto.isNullOrEmpty()) {
            val routePoints =
                pointsDto.map { dto ->
                    RoutePoint(
                        routeId = route.id,
                        seqNo = dto.seqNo,
                        pointGeom =
                            geometryFactory
                                .createPoint(
                                    Coordinate(dto.longitude, dto.latitude),
                                ).also { it.srid = 4326 },
                        elevationM = dto.elevationM,
                    )
                }
            routePointRepository.saveAll(routePoints)

            // Create LineString for pathGeom
            val coordinates =
                pointsDto
                    .sortedBy { it.seqNo }
                    .map { Coordinate(it.longitude, it.latitude) }
                    .toTypedArray()
            if (coordinates.size >= 2) {
                val lineString = geometryFactory.createLineString(coordinates)
                lineString.srid = 4326
                route.pathGeom = lineString
                route = routeRepository.save(route)
            }
        }

        return RouteDto.fromEntity(route, pointsDto)
    }

    @Transactional
    fun updateRoute(
        id: Long,
        request: UpdateRouteRequest,
    ): RouteDto? {
        var route = routeRepository.findById(id).orElse(null) ?: return null

        request.title?.let { route.title = it }
        request.description?.let { route.description = it }
        request.distanceM?.let { route.distanceM = it }
        request.estimatedDurationS?.let { route.estimatedDurationS = it }
        request.difficulty?.let { route.difficulty = it }
        request.isPublic?.let { route.isPublic = it }

        if (request.startPointLat != null && request.startPointLon != null) {
            route.startPoint =
                geometryFactory
                    .createPoint(
                        Coordinate(request.startPointLon, request.startPointLat),
                    ).also { it.srid = 4326 }
        }

        if (request.endPointLat != null && request.endPointLon != null) {
            route.endPoint =
                geometryFactory
                    .createPoint(
                        Coordinate(request.endPointLon, request.endPointLat),
                    ).also { it.srid = 4326 }
        }

        val pointsDto = request.points
        if (pointsDto != null) {
            // Delete existing points
            routePointRepository.deleteByRouteId(id)

            if (pointsDto.isNotEmpty()) {
                val routePoints =
                    pointsDto.map { dto ->
                        RoutePoint(
                            routeId = route.id,
                            seqNo = dto.seqNo,
                            pointGeom =
                                geometryFactory
                                    .createPoint(
                                        Coordinate(dto.longitude, dto.latitude),
                                    ).also { it.srid = 4326 },
                            elevationM = dto.elevationM,
                        )
                    }
                routePointRepository.saveAll(routePoints)

                // Update pathGeom
                val coordinates =
                    pointsDto
                        .sortedBy { it.seqNo }
                        .map { Coordinate(it.longitude, it.latitude) }
                        .toTypedArray()
                if (coordinates.size >= 2) {
                    val lineString = geometryFactory.createLineString(coordinates)
                    lineString.srid = 4326
                    route.pathGeom = lineString
                } else {
                    route.pathGeom = null
                }
            } else {
                route.pathGeom = null
            }
        }

        route.updatedAt = OffsetDateTime.now()
        route = routeRepository.save(route)

        // Fetch points again to be sure or just use the ones from request if updated
        val currentPoints =
            if (pointsDto != null) {
                pointsDto
            } else {
                routePointRepository.findByRouteIdOrderBySeqNoAsc(id).map {
                    RoutePointDto(
                        seqNo = it.seqNo ?: 0,
                        latitude = it.pointGeom?.y ?: 0.0,
                        longitude = it.pointGeom?.x ?: 0.0,
                        elevationM = it.elevationM,
                    )
                }
            }

        return RouteDto.fromEntity(route, currentPoints)
    }

    @Transactional
    fun deleteRoute(id: Long): Boolean {
        if (!routeRepository.existsById(id)) {
            return false
        }
        routePointRepository.deleteByRouteId(id)
        routeRepository.deleteById(id)
        return true
    }

    fun getSimilarRoutes(
        id: Long,
        maxDistance: Double,
        limit: Int,
    ): List<RouteDto> {
        val targetRoute = routeRepository.findById(id).orElse(null) ?: return emptyList()
        val centroid =
            targetRoute.pathGeom?.centroid ?: targetRoute.startPoint ?: return emptyList()

        // 1. Get candidates using spatial index (heuristic)
        // Fetch more candidates than needed to refine with DTW
        val candidateLimit = limit * 5
        val pageRequest = PageRequest.of(0, candidateLimit)
        val candidateRoutes =
            routeRepository.findSimilarRoutes(id, centroid, maxDistance, pageRequest)

        if (candidateRoutes.isEmpty()) return emptyList()

        // 2. Fetch points for target route
        val targetPoints = routePointRepository.findByRouteIdOrderBySeqNoAsc(id)
        if (targetPoints.isEmpty()) return emptyList()

        // 3. Calculate DTW for each candidate and sort
        val scoredRoutes =
            candidateRoutes.map { candidate ->
                val candidatePoints =
                    routePointRepository.findByRouteIdOrderBySeqNoAsc(candidate.id!!)
                val dtwDistance =
                    routeMatchingService.calculateDTWDistance(targetPoints, candidatePoints)
                Pair(candidate, dtwDistance)
            }

        // 4. Return top K
        return scoredRoutes.sortedBy { it.second }.take(limit).map { RouteDto.fromEntity(it.first) }
    }

    fun getNearbyRoutes(
        lat: Double,
        lon: Double,
        radiusMeters: Double,
        page: Int,
        size: Int,
    ): PageResponse<RouteDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val point =
            geometryFactory
                .createPoint(
                    Coordinate(lon, lat),
                ).also { it.srid = 4326 }
        val pageRequest = PageRequest.of(safePage, safeSize)
        val routes = routeRepository.findNearbyRoutes(point, radiusMeters, pageRequest)
        return PageResponse.fromPage(routes) { RouteDto.fromEntity(it) }
    }
}
