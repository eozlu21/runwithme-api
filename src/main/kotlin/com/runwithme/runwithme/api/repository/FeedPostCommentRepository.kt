package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FeedPostComment
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FeedPostCommentRepository : JpaRepository<FeedPostComment, Long> {
    fun findByPostId(
        postId: Long,
        pageable: Pageable,
    ): Page<FeedPostComment>

    fun findByUserId(
        userId: UUID,
        pageable: Pageable,
    ): Page<FeedPostComment>

    @Query("SELECT COUNT(fpc) FROM FeedPostComment fpc WHERE fpc.postId = :postId")
    fun countByPostId(postId: Long): Long
}
