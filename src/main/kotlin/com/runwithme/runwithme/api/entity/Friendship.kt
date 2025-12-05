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
 * Entity representing a friendship between two users.
 * Each friendship is stored as a single row with user1Id < user2Id to avoid duplicates.
 * This ensures that (A, B) and (B, A) are stored as the same friendship.
 */
@Entity
@Table(
    name = "friendships",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_friendship_users",
            columnNames = ["user1_id", "user2_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_friendship_user1", columnList = "user1_id"),
        Index(name = "idx_friendship_user2", columnList = "user2_id"),
    ],
)
open class Friendship(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "friendship_id")
    open var friendshipId: UUID? = null,
    /**
     * The user with the smaller UUID (to ensure consistent ordering and avoid duplicates)
     */
    @Column(name = "user1_id", nullable = false)
    open var user1Id: UUID = UUID.randomUUID(),
    /**
     * The user with the larger UUID (to ensure consistent ordering and avoid duplicates)
     */
    @Column(name = "user2_id", nullable = false)
    open var user2Id: UUID = UUID.randomUUID(),
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    companion object {
        /**
         * Creates a Friendship with users ordered correctly (smaller UUID first).
         */
        fun create(
            userIdA: UUID,
            userIdB: UUID,
        ): Friendship {
            val (first, second) =
                if (userIdA < userIdB) {
                    userIdA to userIdB
                } else {
                    userIdB to userIdA
                }
            return Friendship(
                user1Id = first,
                user2Id = second,
            )
        }
    }
}
