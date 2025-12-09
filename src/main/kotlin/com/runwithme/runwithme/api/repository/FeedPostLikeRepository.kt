package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FeedPostLike
import com.runwithme.runwithme.api.entity.FeedPostLikeId
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FeedPostLikeRepository : JpaRepository<FeedPostLike, FeedPostLikeId> {
    fun findByIdPostId(
        postId: Long,
        pageable: Pageable,
    ): Page<FeedPostLike>

    fun findByIdUserId(
        userId: UUID,
        pageable: Pageable,
    ): Page<FeedPostLike>

    @Query("SELECT COUNT(fpl) FROM FeedPostLike fpl WHERE fpl.id.postId = :postId")
    fun countByPostId(postId: Long): Long

    fun existsByIdPostIdAndIdUserId(
        postId: Long,
        userId: UUID,
    ): Boolean
}
