package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RouteLikeDto
import com.runwithme.runwithme.api.entity.RouteLike
import com.runwithme.runwithme.api.entity.RouteLikeId
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.RouteLikeRepository
import com.runwithme.runwithme.api.repository.RouteRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class RouteLikeService(
    private val routeLikeRepository: RouteLikeRepository,
    private val routeRepository: RouteRepository,
) {
    fun getLikesByRoute(
        routeId: Long,
        page: Int,
        size: Int,
    ): PageResponse<RouteLikeDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val likesPage = routeLikeRepository.findByIdRouteId(routeId, pageRequest)
        return PageResponse.fromPage(likesPage, RouteLikeDto::fromEntity)
    }

    fun getLikesByUser(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<RouteLikeDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val likesPage = routeLikeRepository.findByIdUserId(userId, pageRequest)
        return PageResponse.fromPage(likesPage, RouteLikeDto::fromEntity)
    }

    fun getLikeCount(routeId: Long): Long = routeLikeRepository.countByRouteId(routeId)

    fun isRouteLikedByUser(
        routeId: Long,
        userId: UUID,
    ): Boolean = routeLikeRepository.existsByIdRouteIdAndIdUserId(routeId, userId)

    @Transactional
    fun likeRoute(
        routeId: Long,
        authenticatedUserId: UUID,
    ): RouteLikeDto {
        // Check if route exists
        if (!routeRepository.existsById(routeId)) {
            throw IllegalArgumentException("Route with ID $routeId does not exist")
        }

        // Check if already liked
        val likeId = RouteLikeId(routeId = routeId, userId = authenticatedUserId)
        if (routeLikeRepository.existsById(likeId)) {
            throw IllegalArgumentException("You have already liked this route")
        }

        val routeLike = RouteLike(id = likeId)
        val savedLike = routeLikeRepository.save(routeLike)
        return RouteLikeDto.fromEntity(savedLike)
    }

    @Transactional
    fun unlikeRoute(
        routeId: Long,
        authenticatedUserId: UUID,
    ): Boolean {
        val likeId = RouteLikeId(routeId = routeId, userId = authenticatedUserId)
        val like = routeLikeRepository.findById(likeId).orElse(null) ?: return false

        // Users can only unlike their own likes
        if (like.id.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only unlike your own likes")
        }

        routeLikeRepository.deleteById(likeId)
        return true
    }
}
