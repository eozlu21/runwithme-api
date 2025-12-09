package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.FeedPostLikeDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.entity.FeedPostLike
import com.runwithme.runwithme.api.entity.FeedPostLikeId
import com.runwithme.runwithme.api.repository.FeedPostLikeRepository
import com.runwithme.runwithme.api.repository.FeedPostRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedPostLikeService(
    private val feedPostLikeRepository: FeedPostLikeRepository,
    private val feedPostRepository: FeedPostRepository,
) {
    fun getLikesByPost(
        postId: Long,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostLikeDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val likesPage = feedPostLikeRepository.findByIdPostId(postId, pageRequest)
        return PageResponse.fromPage(likesPage, FeedPostLikeDto::fromEntity)
    }

    fun getLikesByUser(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostLikeDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val likesPage = feedPostLikeRepository.findByIdUserId(userId, pageRequest)
        return PageResponse.fromPage(likesPage, FeedPostLikeDto::fromEntity)
    }

    fun getLikeCount(postId: Long): Long = feedPostLikeRepository.countByPostId(postId)

    fun isPostLikedByUser(
        postId: Long,
        userId: UUID,
    ): Boolean = feedPostLikeRepository.existsByIdPostIdAndIdUserId(postId, userId)

    @Transactional
    fun likePost(
        postId: Long,
        authenticatedUserId: UUID,
    ): FeedPostLikeDto {
        // Check if post exists
        if (!feedPostRepository.existsById(postId)) {
            throw IllegalArgumentException("Post with ID $postId does not exist")
        }

        // Check if already liked
        val likeId = FeedPostLikeId(postId = postId, userId = authenticatedUserId)
        if (feedPostLikeRepository.existsById(likeId)) {
            throw IllegalArgumentException("You have already liked this post")
        }

        val postLike = FeedPostLike(id = likeId)
        val savedLike = feedPostLikeRepository.save(postLike)
        return FeedPostLikeDto.fromEntity(savedLike)
    }

    @Transactional
    fun unlikePost(
        postId: Long,
        authenticatedUserId: UUID,
    ): Boolean {
        val likeId = FeedPostLikeId(postId = postId, userId = authenticatedUserId)
        val like = feedPostLikeRepository.findById(likeId).orElse(null) ?: return false

        // Users can only unlike their own likes

        feedPostLikeRepository.deleteById(likeId)
        return true
    }
}
