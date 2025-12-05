package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.Friendship
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface FriendshipRepository : JpaRepository<Friendship, UUID> {
    /**
     * Find all friendships for a user (where user is either user1 or user2)
     */
    @Query(
        """
        SELECT f FROM Friendship f 
        WHERE f.user1Id = :userId OR f.user2Id = :userId
    """,
    )
    fun findAllByUserId(
        @Param("userId") userId: UUID,
        pageable: Pageable,
    ): Page<Friendship>

    /**
     * Find all friendships for a user (non-paginated)
     */
    @Query(
        """
        SELECT f FROM Friendship f 
        WHERE f.user1Id = :userId OR f.user2Id = :userId
    """,
    )
    fun findAllByUserId(
        @Param("userId") userId: UUID,
    ): List<Friendship>

    /**
     * Find a specific friendship between two users
     */
    @Query(
        """
        SELECT f FROM Friendship f 
        WHERE (f.user1Id = :userId1 AND f.user2Id = :userId2)
           OR (f.user1Id = :userId2 AND f.user2Id = :userId1)
    """,
    )
    fun findBetweenUsers(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Optional<Friendship>

    /**
     * Check if two users are friends
     */
    @Query(
        """
        SELECT COUNT(f) > 0 FROM Friendship f 
        WHERE (f.user1Id = :userId1 AND f.user2Id = :userId2)
           OR (f.user1Id = :userId2 AND f.user2Id = :userId1)
    """,
    )
    fun areFriends(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Boolean

    /**
     * Get all friend IDs for a user
     */
    @Query(
        """
        SELECT CASE 
            WHEN f.user1Id = :userId THEN f.user2Id 
            ELSE f.user1Id 
        END 
        FROM Friendship f 
        WHERE f.user1Id = :userId OR f.user2Id = :userId
    """,
    )
    fun findFriendIds(
        @Param("userId") userId: UUID,
    ): List<UUID>

    /**
     * Count the number of friends for a user
     */
    @Query(
        """
        SELECT COUNT(f) FROM Friendship f 
        WHERE f.user1Id = :userId OR f.user2Id = :userId
    """,
    )
    fun countFriends(
        @Param("userId") userId: UUID,
    ): Long

    /**
     * Delete a friendship between two users
     */
    @Modifying
    @Query(
        """
        DELETE FROM Friendship f 
        WHERE (f.user1Id = :userId1 AND f.user2Id = :userId2)
           OR (f.user1Id = :userId2 AND f.user2Id = :userId1)
    """,
    )
    fun deleteBetweenUsers(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    )
}
