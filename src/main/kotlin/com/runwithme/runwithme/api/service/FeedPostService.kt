package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateFeedPostRequest
import com.runwithme.runwithme.api.dto.FeedPostDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.RoutePointDto
import com.runwithme.runwithme.api.dto.RunSessionPointDto
import com.runwithme.runwithme.api.dto.UpdateFeedPostRequest
import com.runwithme.runwithme.api.entity.FeedPost
import com.runwithme.runwithme.api.entity.FeedPostRoute
import com.runwithme.runwithme.api.entity.FeedPostRunSession
import com.runwithme.runwithme.api.entity.PostType
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.FeedPostCommentRepository
import com.runwithme.runwithme.api.repository.FeedPostLikeRepository
import com.runwithme.runwithme.api.repository.FeedPostRepository
import com.runwithme.runwithme.api.repository.FeedPostRouteRepository
import com.runwithme.runwithme.api.repository.FeedPostRunSessionRepository
import com.runwithme.runwithme.api.repository.FriendshipRepository
import com.runwithme.runwithme.api.repository.RoutePointRepository
import com.runwithme.runwithme.api.repository.RouteRepository
import com.runwithme.runwithme.api.repository.RunSessionPointRepository
import com.runwithme.runwithme.api.repository.RunSessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedPostService(
    private val feedPostRepository: FeedPostRepository,
    private val feedPostLikeRepository: FeedPostLikeRepository,
    private val feedPostCommentRepository: FeedPostCommentRepository,
    private val feedPostRouteRepository: FeedPostRouteRepository,
    private val feedPostRunSessionRepository: FeedPostRunSessionRepository,
    private val friendshipRepository: FriendshipRepository,
    private val routeRepository: RouteRepository,
    private val routePointRepository: RoutePointRepository,
    private val runSessionRepository: RunSessionRepository,
    private val runSessionPointRepository: RunSessionPointRepository,
) {
    private fun createPageRequest(
        page: Int,
        size: Int,
    ): PageRequest {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        return PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
    }

    private fun getFriendIds(userId: UUID): List<UUID> {
        val friendships = friendshipRepository.findAllByUserId(userId)
        return friendships.map { friendship ->
            if (friendship.user1Id == userId) friendship.user2Id else friendship.user1Id
        }
    }

    // Batch enrichment for a list of posts to avoid N+1 queries
    fun enrichPostDtos(
        posts: List<FeedPost>,
        currentUserId: UUID?,
    ): List<FeedPostDto> =
        posts.map { post ->
            val likeCount = feedPostLikeRepository.countByPostId(post.id!!)
            val commentCount = feedPostCommentRepository.countByPostId(post.id!!)
            val isLikedByCurrentUser =
                currentUserId?.let {
                    feedPostLikeRepository.existsByIdPostIdAndIdUserId(post.id!!, it)
                }
            val linkedRouteId = feedPostRouteRepository.findByPostId(post.id!!).orElse(null)?.routeId
            val linkedRunSessionId = feedPostRunSessionRepository.findByPostId(post.id!!).orElse(null)?.runSessionId

            // Fetch route details if linkedRouteId is present
            var routeDistanceM: Double? = null
            var routeTitle: String? = null
            var startPointLat: Double? = null
            var startPointLon: Double? = null
            var endPointLat: Double? = null
            var endPointLon: Double? = null
            var routePoints: List<RoutePointDto>? = null

            if (linkedRouteId != null) {
                val route = routeRepository.findById(linkedRouteId).orElse(null)
                if (route != null) {
                    routeDistanceM = route.distanceM
                    routeTitle = route.title
                    startPointLat = route.startPoint?.y
                    startPointLon = route.startPoint?.x
                    endPointLat = route.endPoint?.y
                    endPointLon = route.endPoint?.x
                    routePoints =
                        routePointRepository.findByRouteIdOrderBySeqNoAsc(linkedRouteId).map {
                            RoutePointDto(
                                seqNo = it.seqNo ?: 0,
                                latitude = it.pointGeom?.y ?: 0.0,
                                longitude = it.pointGeom?.x ?: 0.0,
                                elevationM = it.elevationM,
                            )
                        }
                }
            }

            // Fetch run session details if linkedRunSessionId is present
            var runDistanceM: Double? = null
            var runDurationS: Int? = null
            var runPaceSecPerKm: Double? = null
            var runPoints: List<RunSessionPointDto>? = null

            if (linkedRunSessionId != null) {
                val runSession = runSessionRepository.findById(linkedRunSessionId).orElse(null)
                if (runSession != null) {
                    runDistanceM = runSession.totalDistanceM
                    runDurationS = runSession.movingTimeS
                    runPaceSecPerKm = runSession.avgPaceSecPerKm
                    runPoints =
                        runSessionPointRepository.findByRunSessionIdOrderBySeqNoAsc(linkedRunSessionId).map {
                            RunSessionPointDto.fromEntity(it)
                        }
                }
            }

            FeedPostDto.fromEntity(
                post = post,
                likeCount = likeCount,
                commentCount = commentCount,
                isLikedByCurrentUser = isLikedByCurrentUser,
                linkedRouteId = linkedRouteId,
                linkedRunSessionId = linkedRunSessionId,
                routeDistanceM = routeDistanceM,
                routeTitle = routeTitle,
                startPointLat = startPointLat,
                startPointLon = startPointLon,
                endPointLat = endPointLat,
                endPointLon = endPointLon,
                routePoints = routePoints,
                runDistanceM = runDistanceM,
                runDurationS = runDurationS,
                runPaceSecPerKm = runPaceSecPerKm,
                runPoints = runPoints,
            )
        }

    // Retain single-post enrichment for backward compatibility (calls batch method for one post)
    private fun enrichPostDto(
        post: FeedPost,
        currentUserId: UUID?,
    ): FeedPostDto = enrichPostDtos(listOf(post), currentUserId).first()

    fun getPublicFeed(
        page: Int,
        size: Int,
        currentUserId: UUID?,
    ): PageResponse<FeedPostDto> {
        val pageRequest = createPageRequest(page, size)
        val postPage = feedPostRepository.findPublicPosts(pageRequest)
        return PageResponse.fromPage(postPage) { enrichPostDto(it, currentUserId) }
    }

    fun getPersonalizedFeed(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostDto> {
        val pageRequest = createPageRequest(page, size)
        val friendIds = getFriendIds(userId)
        val postPage = feedPostRepository.findFeedForUser(userId, friendIds, pageRequest)
        return PageResponse.fromPage(postPage) { enrichPostDto(it, userId) }
    }

    fun getPostsByUser(
        authorId: UUID,
        requesterId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostDto> {
        val pageRequest = createPageRequest(page, size)
        val friendIds = getFriendIds(requesterId)
        val postPage = feedPostRepository.findByAuthorIdVisibleTo(authorId, requesterId, friendIds, pageRequest)
        return PageResponse.fromPage(postPage) { enrichPostDto(it, requesterId) }
    }

    fun getPostById(
        postId: Long,
        currentUserId: UUID?,
    ): FeedPostDto? {
        val post = feedPostRepository.findById(postId).orElse(null) ?: return null
        return enrichPostDto(post, currentUserId)
    }

    @Transactional
    fun createPost(
        request: CreateFeedPostRequest,
        authorId: UUID,
    ): FeedPostDto {
        // Validate based on post type
        when (request.postType) {
            PostType.ROUTE -> {
                if (request.routeId == null) {
                    throw IllegalArgumentException("Route ID is required for ROUTE type posts")
                }
                if (!routeRepository.existsById(request.routeId)) {
                    throw IllegalArgumentException("Route with ID ${request.routeId} does not exist")
                }
            }
            PostType.RUN_SESSION -> {
                if (request.runSessionId == null) {
                    throw IllegalArgumentException("Run session ID is required for RUN_SESSION type posts")
                }
                // Note: Add run session validation when RunSessionRepository is available
            }
            PostType.TEXT -> {
                if (request.textContent.isNullOrBlank()) {
                    throw IllegalArgumentException("Text content is required for TEXT type posts")
                }
            }
        }

        val post =
            FeedPost(
                authorId = authorId,
                postType = request.postType,
                textContent = request.textContent,
                mediaUrl = request.mediaUrl,
                visibility = request.visibility,
            )

        val savedPost = feedPostRepository.save(post)

        // Create mapping based on post type
        when (request.postType) {
            PostType.ROUTE -> {
                val postRoute =
                    FeedPostRoute(
                        postId = savedPost.id!!,
                        routeId = request.routeId!!,
                    )
                feedPostRouteRepository.save(postRoute)
            }
            PostType.RUN_SESSION -> {
                val postRunSession =
                    FeedPostRunSession(
                        postId = savedPost.id!!,
                        runSessionId = request.runSessionId!!,
                    )
                feedPostRunSessionRepository.save(postRunSession)
            }
            else -> {
                // No mapping needed for TEXT posts
            }
        }

        return enrichPostDto(savedPost, authorId)
    }

    @Transactional
    fun updatePost(
        postId: Long,
        request: UpdateFeedPostRequest,
        authenticatedUserId: UUID,
    ): FeedPostDto {
        val post =
            feedPostRepository.findById(postId).orElseThrow {
                NoSuchElementException("Post with ID $postId not found")
            }

        if (post.authorId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only update your own posts")
        }

        request.textContent?.let { post.textContent = it }
        request.mediaUrl?.let { post.mediaUrl = it }
        request.visibility?.let { post.visibility = it }

        val updatedPost = feedPostRepository.save(post)
        return enrichPostDto(updatedPost, authenticatedUserId)
    }

    @Transactional
    fun deletePost(
        postId: Long,
        authenticatedUserId: UUID,
    ): Boolean {
        val post = feedPostRepository.findById(postId).orElse(null) ?: return false

        if (post.authorId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only delete your own posts")
        }

        // Delete mappings first (due to foreign key constraints)
        feedPostRouteRepository.deleteByPostId(postId)
        feedPostRunSessionRepository.deleteByPostId(postId)

        feedPostRepository.deleteById(postId)
        return true
    }
}
