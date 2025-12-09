package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FeedPost
import com.runwithme.runwithme.api.entity.PostVisibility
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FeedPostRepository : JpaRepository<FeedPost, Long> {
    fun findByAuthorId(
        authorId: UUID,
        pageable: Pageable,
    ): Page<FeedPost>

    fun findByVisibility(
        visibility: PostVisibility,
        pageable: Pageable,
    ): Page<FeedPost>

    @Query(
        """
        SELECT fp FROM FeedPost fp 
        WHERE fp.visibility = 'PUBLIC' 
        OR fp.authorId = :userId 
        OR (fp.visibility = 'FRIENDS' AND fp.authorId IN :friendIds)
        ORDER BY fp.createdAt DESC
        """,
    )
    fun findFeedForUser(
        userId: UUID,
        friendIds: List<UUID>,
        pageable: Pageable,
    ): Page<FeedPost>

    @Query(
        """
        SELECT fp FROM FeedPost fp 
        WHERE fp.visibility = 'PUBLIC'
        ORDER BY fp.createdAt DESC
        """,
    )
    fun findPublicPosts(pageable: Pageable): Page<FeedPost>

    @Query(
        """
        SELECT fp FROM FeedPost fp 
        WHERE fp.authorId = :authorId 
        AND (fp.visibility = 'PUBLIC' OR fp.authorId = :requesterId OR 
            (fp.visibility = 'FRIENDS' AND fp.authorId IN :friendIds))
        ORDER BY fp.createdAt DESC
        """,
    )
    fun findByAuthorIdVisibleTo(
        authorId: UUID,
        requesterId: UUID,
        friendIds: List<UUID>,
        pageable: Pageable,
    ): Page<FeedPost>
}
