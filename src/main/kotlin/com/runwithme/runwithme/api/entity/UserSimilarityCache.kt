package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity storing precomputed similarity scores between user pairs.
 * User IDs are stored in sorted order (user1Id < user2Id) to avoid duplicates,
 * following the same pattern as Friendship entity.
 */
@Entity
@Table(
    name = "user_similarity_cache",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_similarity_users",
            columnNames = ["user1_id", "user2_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_similarity_user1", columnList = "user1_id"),
        Index(name = "idx_similarity_user2", columnList = "user2_id"),
        Index(name = "idx_similarity_combined_score", columnList = "combined_score"),
    ],
)
open class UserSimilarityCache(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "user1_id", nullable = false)
    open var user1Id: UUID = UUID.randomUUID(),
    @Column(name = "user2_id", nullable = false)
    open var user2Id: UUID = UUID.randomUUID(),
    @Column(name = "route_similarity", nullable = false)
    open var routeSimilarity: Double = 0.0,
    @Column(name = "preference_similarity", nullable = false)
    open var preferenceSimilarity: Double = 0.0,
    @Column(name = "combined_score", nullable = false)
    open var combinedScore: Double = 0.0,
    @Column(name = "computed_at", nullable = false)
    open var computedAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        /**
         * Creates a UserSimilarityCache with users ordered correctly (smaller UUID first).
         */
        fun create(
            userIdA: UUID,
            userIdB: UUID,
            routeSimilarity: Double,
            preferenceSimilarity: Double,
            combinedScore: Double,
        ): UserSimilarityCache {
            val (first, second) =
                if (userIdA < userIdB) {
                    userIdA to userIdB
                } else {
                    userIdB to userIdA
                }
            return UserSimilarityCache(
                user1Id = first,
                user2Id = second,
                routeSimilarity = routeSimilarity,
                preferenceSimilarity = preferenceSimilarity,
                combinedScore = combinedScore,
            )
        }
    }
}
