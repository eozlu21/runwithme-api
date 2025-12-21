package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.AddRunSessionPointsRequest
import com.runwithme.runwithme.api.dto.EndRunSessionRequest
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RunSessionDto
import com.runwithme.runwithme.api.dto.RunSessionPointDto
import com.runwithme.runwithme.api.dto.StartRunSessionRequest
import com.runwithme.runwithme.api.dto.UpdateRunSessionRequest
import com.runwithme.runwithme.api.entity.RunSession
import com.runwithme.runwithme.api.entity.RunSessionPoint
import com.runwithme.runwithme.api.repository.RoutePointRepository
import com.runwithme.runwithme.api.repository.RouteRepository
import com.runwithme.runwithme.api.repository.RunSessionPointRepository
import com.runwithme.runwithme.api.repository.RunSessionRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
class RunSessionService(
    private val runSessionRepository: RunSessionRepository,
    private val runSessionPointRepository: RunSessionPointRepository,
    private val userRepository: UserRepository,
    private val routeRepository: RouteRepository,
    private val routePointRepository: RoutePointRepository,
    private val userStatisticsService: UserStatisticsService,
) {
    private val geometryFactory = GeometryFactory()

    fun getRunSessionById(id: Long): RunSessionDto? {
        val session = runSessionRepository.findById(id).orElse(null) ?: return null
        val points = runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(id).map { RunSessionPointDto.fromEntity(it) }
        return RunSessionDto.fromEntity(session, points)
    }

    fun getRunSessionsByUser(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<RunSessionDto> {
        val pageRequest = PageRequest.of(sanitizePage(page), sanitizeSize(size), Sort.by("createdAt").descending())
        val sessionPage = runSessionRepository.findByUserId(userId, pageRequest)
        return PageResponse.fromPage(sessionPage) { RunSessionDto.fromEntity(it) }
    }

    fun getPublicRunSessions(
        page: Int,
        size: Int,
    ): PageResponse<RunSessionDto> {
        val pageRequest = PageRequest.of(sanitizePage(page), sanitizeSize(size), Sort.by("createdAt").descending())
        val sessionPage = runSessionRepository.findByIsPublicTrue(pageRequest)
        return PageResponse.fromPage(sessionPage) { RunSessionDto.fromEntity(it) }
    }

    fun getRunSessionsByRoute(
        routeId: Long,
        page: Int,
        size: Int,
    ): PageResponse<RunSessionDto> {
        val pageRequest = PageRequest.of(sanitizePage(page), sanitizeSize(size), Sort.by("createdAt").descending())
        val sessionPage = runSessionRepository.findByRouteId(routeId, pageRequest)
        return PageResponse.fromPage(sessionPage) { RunSessionDto.fromEntity(it) }
    }

    fun getActiveSessionsForUser(userId: UUID): List<RunSessionDto> {
        val sessions = runSessionRepository.findByUserIdAndEndedAtIsNull(userId)
        return sessions.map { RunSessionDto.fromEntity(it) }
    }

    @Transactional
    fun createRunSessionFromRoute(
        routeId: Long,
        username: String,
    ): RunSessionDto {
        val user = userRepository.findByUsername(username).orElseThrow { RuntimeException("User not found: $username") }
        val route = routeRepository.findById(routeId).orElseThrow { RuntimeException("Route not found: $routeId") }
        val routePoints = routePointRepository.findByRouteIdOrderBySeqNoAsc(routeId)

        val now = OffsetDateTime.now()
        val durationS = route.estimatedDurationS ?: 3600 // Default to 1 hour if null
        val endedAt = now.plusSeconds(durationS.toLong())

        // Calculate pace
        val distanceKm = (route.distanceM ?: 0.0) / 1000.0
        val avgPace = if (distanceKm > 0) durationS / distanceKm else 0.0

        val session =
            RunSession(
                userId = user.userId,
                routeId = route.id,
                isPublic = false, // Default to private for synthetic runs
                startedAt = now,
                endedAt = endedAt,
                movingTimeS = durationS,
                geomTrack = route.pathGeom,
                createdAt = now,
                totalDistanceM = route.distanceM,
                avgPaceSecPerKm = avgPace,
                elevationGainM = 0.0, // Could calculate, but 0 is safe for now
            )

        val savedSession = runSessionRepository.save(session)

        val sessionPoints =
            routePoints.mapIndexed { index, rp ->
                // Distribute time linearly
                val timeOffsetS =
                    if (routePoints.size > 1) {
                        (index.toDouble() / (routePoints.size - 1)) * durationS
                    } else {
                        0.0
                    }

                RunSessionPoint(
                    runSessionId = savedSession.id,
                    seqNo = rp.seqNo,
                    pointGeom = rp.pointGeom,
                    elevationM = rp.elevationM,
                    recordedAt = now.plusSeconds(timeOffsetS.toLong()),
                )
            }

        runSessionPointRepository.saveAll(sessionPoints)

        val pointDtos = sessionPoints.map { RunSessionPointDto.fromEntity(it) }
        return RunSessionDto.fromEntity(savedSession, pointDtos)
    }

    @Transactional
    fun startRunSession(
        request: StartRunSessionRequest,
        username: String,
    ): RunSessionDto {
        val user = userRepository.findByUsername(username).orElseThrow { RuntimeException("User not found: $username") }

        val session =
            RunSession(
                userId = user.userId,
                routeId = request.routeId,
                isPublic = request.isPublic,
                startedAt = OffsetDateTime.now(),
                createdAt = OffsetDateTime.now(),
            )
        val savedSession = runSessionRepository.save(session)

        var points: List<RunSessionPointDto>? = null
        if (request.initialPoint != null) {
            val point = createRunSessionPoint(savedSession.id!!, request.initialPoint, 1)
            runSessionPointRepository.save(point)
            points = listOf(request.initialPoint.copy(seqNo = 1))
        }

        return RunSessionDto.fromEntity(savedSession, points)
    }

    @Transactional
    fun addPoints(
        sessionId: Long,
        request: AddRunSessionPointsRequest,
    ): RunSessionDto {
        val session =
            runSessionRepository.findById(sessionId).orElseThrow { RuntimeException("Run session not found: $sessionId") }

        if (session.endedAt != null) {
            throw RuntimeException("Cannot add points to ended session: $sessionId")
        }

        val currentMaxSeqNo = runSessionPointRepository.findMaxSeqNoByRunSessionId(sessionId)
        var nextSeqNo = currentMaxSeqNo + 1

        val pointsToSave =
            request.points.map { dto ->
                val point = createRunSessionPoint(sessionId, dto, nextSeqNo)
                nextSeqNo++
                point
            }

        runSessionPointRepository.saveAll(pointsToSave)

        val allPoints = runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(sessionId).map { RunSessionPointDto.fromEntity(it) }
        return RunSessionDto.fromEntity(session, allPoints)
    }

    @Transactional
    fun addSinglePoint(
        sessionId: Long,
        point: RunSessionPointDto,
    ): RunSessionDto = addPoints(sessionId, AddRunSessionPointsRequest(listOf(point)))

    @Transactional
    fun endRunSession(
        sessionId: Long,
        request: EndRunSessionRequest?,
    ): RunSessionDto {
        val session =
            runSessionRepository.findById(sessionId).orElseThrow { RuntimeException("Run session not found: $sessionId") }

        if (session.endedAt != null) {
            throw RuntimeException("Session already ended: $sessionId")
        }

        // Add final point if provided
        if (request?.finalPoint != null) {
            val currentMaxSeqNo = runSessionPointRepository.findMaxSeqNoByRunSessionId(sessionId)
            val point = createRunSessionPoint(sessionId, request.finalPoint, currentMaxSeqNo + 1)
            runSessionPointRepository.save(point)
        }

        // Update visibility if requested
        if (request?.isPublic != null) {
            session.isPublic = request.isPublic
        }

        // Set end time
        session.endedAt = OffsetDateTime.now()

        // Compute stats and geom_track from points
        computeSessionStats(session)

        val savedSession = runSessionRepository.save(session)

        if (savedSession.userId != null) {
            userStatisticsService.updateStatistics(savedSession.userId!!)
        }

        val allPoints = runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(sessionId).map { RunSessionPointDto.fromEntity(it) }
        return RunSessionDto.fromEntity(savedSession, allPoints)
    }

    @Transactional
    fun updateRunSession(
        sessionId: Long,
        request: UpdateRunSessionRequest,
    ): RunSessionDto? {
        val session = runSessionRepository.findById(sessionId).orElse(null) ?: return null

        request.isPublic?.let { session.isPublic = it }
        request.routeId?.let { session.routeId = it }

        val savedSession = runSessionRepository.save(session)
        val points = runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(sessionId).map { RunSessionPointDto.fromEntity(it) }
        return RunSessionDto.fromEntity(savedSession, points)
    }

    @Transactional
    fun deleteRunSession(sessionId: Long): Boolean {
        val session = runSessionRepository.findById(sessionId).orElse(null) ?: return false
        val userId = session.userId

        runSessionPointRepository.deleteByRunSessionId(sessionId)
        runSessionRepository.delete(session)

        if (userId != null) {
            userStatisticsService.updateStatistics(userId)
        }
        return true
    }

    private fun createRunSessionPoint(
        sessionId: Long,
        dto: RunSessionPointDto,
        seqNo: Int,
    ): RunSessionPoint =
        RunSessionPoint(
            runSessionId = sessionId,
            seqNo = seqNo,
            pointGeom =
                geometryFactory.createPoint(Coordinate(dto.longitude, dto.latitude)).also {
                    it.srid = 4326
                },
            elevationM = dto.elevationM,
            recordedAt = dto.recordedAt ?: OffsetDateTime.now(),
        )

    private fun computeSessionStats(session: RunSession) {
        val points = runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(session.id!!)

        if (points.size < 2) {
            session.totalDistanceM = 0.0
            session.elevationGainM = 0.0
            session.avgPaceSecPerKm = null
            session.movingTimeS = null
            session.geomTrack = null
            return
        }

        // Compute geom_track LineString
        val coordinates =
            points.mapNotNull { it.pointGeom }.map { Coordinate(it.x, it.y) }.toTypedArray()

        if (coordinates.size >= 2) {
            val lineString = geometryFactory.createLineString(coordinates)
            lineString.srid = 4326
            session.geomTrack = lineString
        }

        // Compute total distance using Haversine
        var totalDistance = 0.0
        for (i in 0 until points.size - 1) {
            val p1 = points[i].pointGeom
            val p2 = points[i + 1].pointGeom
            if (p1 != null && p2 != null) {
                totalDistance += haversineDistance(p1.y, p1.x, p2.y, p2.x)
            }
        }
        session.totalDistanceM = totalDistance

        // Compute elevation gain (only positive changes)
        var elevationGain = 0.0
        for (i in 0 until points.size - 1) {
            val e1 = points[i].elevationM
            val e2 = points[i + 1].elevationM
            if (e1 != null && e2 != null && e2 > e1) {
                elevationGain += (e2 - e1)
            }
        }
        session.elevationGainM = elevationGain

        // Compute moving time from timestamps
        val firstTime = points.firstOrNull()?.recordedAt
        val lastTime = points.lastOrNull()?.recordedAt
        if (firstTime != null && lastTime != null) {
            val durationSeconds =
                java.time.Duration
                    .between(firstTime, lastTime)
                    .seconds
                    .toInt()
            session.movingTimeS = durationSeconds

            // Compute average pace (seconds per km)
            if (totalDistance > 0) {
                session.avgPaceSecPerKm = (durationSeconds.toDouble() / totalDistance) * 1000.0
            }
        }
    }

    private fun haversineDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusM = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusM * c
    }

    private fun sanitizePage(page: Int): Int = if (page < 0) 0 else page

    private fun sanitizeSize(size: Int): Int =
        when {
            size < 1 -> 10
            size > 100 -> 100
            else -> size
        }
}
