package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FeedPostRunSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FeedPostRunSessionRepository : JpaRepository<FeedPostRunSession, Long> {
    fun findByPostId(postId: Long): Optional<FeedPostRunSession>

    fun findByRunSessionId(runSessionId: Long): List<FeedPostRunSession>

    fun existsByPostId(postId: Long): Boolean

    fun deleteByPostId(postId: Long)
}
