package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.RunSession
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

@Repository
interface RunSessionRepository : JpaRepository<RunSession, Long> {
    fun findByUserId(
        userId: UUID,
        pageable: Pageable,
    ): Page<RunSession>

    fun findByUserIdAndEndedAtIsNull(userId: UUID): List<RunSession>

    fun findByRouteId(
        routeId: Long,
        pageable: Pageable,
    ): Page<RunSession>

    fun findByIsPublicTrue(pageable: Pageable): Page<RunSession>

    fun findByUserIdAndIsPublicTrue(
        userId: UUID,
        pageable: Pageable,
    ): Page<RunSession>

    fun findAllByUserId(userId: UUID): List<RunSession>

    fun findAllByUserIdAndStartedAtGreaterThanEqual(
        userId: UUID,
        since: OffsetDateTime,
    ): List<RunSession>
}
