package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FeedPostRoute
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FeedPostRouteRepository : JpaRepository<FeedPostRoute, Long> {
    fun findByPostId(postId: Long): Optional<FeedPostRoute>

    fun findByRouteId(routeId: Long): List<FeedPostRoute>

    fun existsByPostId(postId: Long): Boolean

    fun deleteByPostId(postId: Long)
}
