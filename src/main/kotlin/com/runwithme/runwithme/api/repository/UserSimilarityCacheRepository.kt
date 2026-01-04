package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.UserSimilarityCache
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface UserSimilarityCacheRepository : JpaRepository<UserSimilarityCache, Long> {
    /**
     * Find similarity cache entry for a specific user pair (handles ordering automatically)
     */
    @Query(
        """
        SELECT c FROM UserSimilarityCache c
        WHERE (c.user1Id = :userId1 AND c.user2Id = :userId2)
           OR (c.user1Id = :userId2 AND c.user2Id = :userId1)
        """,
    )
    fun findByUserPair(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Optional<UserSimilarityCache>

    /**
     * Find all cached similarities for a user (where user is either user1 or user2)
     */
    @Query(
        """
        SELECT c FROM UserSimilarityCache c
        WHERE c.user1Id = :userId OR c.user2Id = :userId
        """,
    )
    fun findAllByUserId(
        @Param("userId") userId: UUID,
    ): List<UserSimilarityCache>

    /**
     * Find all cached similarities for a user, ordered by combined score descending
     */
    @Query(
        """
        SELECT c FROM UserSimilarityCache c
        WHERE c.user1Id = :userId OR c.user2Id = :userId
        ORDER BY c.combinedScore DESC
        """,
    )
    fun findAllByUserIdOrderByCombinedScoreDesc(
        @Param("userId") userId: UUID,
    ): List<UserSimilarityCache>

    /**
     * Delete all cache entries for a specific user (for recomputation)
     */
    @Modifying
    @Query(
        """
        DELETE FROM UserSimilarityCache c
        WHERE c.user1Id = :userId OR c.user2Id = :userId
        """,
    )
    fun deleteAllByUserId(
        @Param("userId") userId: UUID,
    )

    /**
     * Delete all cache entries (for full recomputation)
     */
    @Modifying
    @Query("DELETE FROM UserSimilarityCache c")
    fun deleteAllEntries()

    /**
     * Check if cache exists for a user pair
     */
    @Query(
        """
        SELECT COUNT(c) > 0 FROM UserSimilarityCache c
        WHERE (c.user1Id = :userId1 AND c.user2Id = :userId2)
           OR (c.user1Id = :userId2 AND c.user2Id = :userId1)
        """,
    )
    fun existsByUserPair(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Boolean

    /**
     * Count total cache entries
     */
    @Query("SELECT COUNT(c) FROM UserSimilarityCache c")
    fun countEntries(): Long
}
