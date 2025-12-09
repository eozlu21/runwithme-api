package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateCommentRequest
import com.runwithme.runwithme.api.dto.FeedPostCommentDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.dto.UpdateCommentRequest
import com.runwithme.runwithme.api.entity.FeedPostComment
import com.runwithme.runwithme.api.exception.UnauthorizedActionException
import com.runwithme.runwithme.api.repository.FeedPostCommentRepository
import com.runwithme.runwithme.api.repository.FeedPostRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class FeedPostCommentService(
    private val feedPostCommentRepository: FeedPostCommentRepository,
    private val feedPostRepository: FeedPostRepository,
) {
    fun getCommentsByPost(
        postId: Long,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostCommentDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").ascending())
        val commentsPage = feedPostCommentRepository.findByPostId(postId, pageRequest)
        return PageResponse.fromPage(commentsPage, FeedPostCommentDto::fromEntity)
    }

    fun getCommentsByUser(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<FeedPostCommentDto> {
        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }
        val pageRequest = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending())
        val commentsPage = feedPostCommentRepository.findByUserId(userId, pageRequest)
        return PageResponse.fromPage(commentsPage, FeedPostCommentDto::fromEntity)
    }

    fun getCommentById(commentId: Long): FeedPostCommentDto? = feedPostCommentRepository.findById(commentId).map(FeedPostCommentDto::fromEntity).orElse(null)

    fun getCommentCount(postId: Long): Long = feedPostCommentRepository.countByPostId(postId)

    @Transactional
    fun createComment(
        postId: Long,
        request: CreateCommentRequest,
        authenticatedUserId: UUID,
    ): FeedPostCommentDto {
        // Check if post exists
        if (!feedPostRepository.existsById(postId)) {
            throw IllegalArgumentException("Post with ID $postId does not exist")
        }

        if (request.commentText.isBlank()) {
            throw IllegalArgumentException("Comment text cannot be empty")
        }

        val comment =
            FeedPostComment(
                postId = postId,
                userId = authenticatedUserId,
                commentText = request.commentText,
            )

        val savedComment = feedPostCommentRepository.save(comment)
        return FeedPostCommentDto.fromEntity(savedComment)
    }

    @Transactional
    fun updateComment(
        commentId: Long,
        request: UpdateCommentRequest,
        authenticatedUserId: UUID,
    ): FeedPostCommentDto {
        val comment =
            feedPostCommentRepository.findById(commentId).orElseThrow {
                NoSuchElementException("Comment with ID $commentId not found")
            }

        if (comment.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only update your own comments")
        }

        if (request.commentText.isBlank()) {
            throw IllegalArgumentException("Comment text cannot be empty")
        }

        comment.commentText = request.commentText
        val updatedComment = feedPostCommentRepository.save(comment)
        return FeedPostCommentDto.fromEntity(updatedComment)
    }

    @Transactional
    fun deleteComment(
        commentId: Long,
        authenticatedUserId: UUID,
    ): Boolean {
        val comment = feedPostCommentRepository.findById(commentId).orElse(null) ?: return false

        if (comment.userId != authenticatedUserId) {
            throw UnauthorizedActionException("You can only delete your own comments")
        }

        feedPostCommentRepository.deleteById(commentId)
        return true
    }
}
