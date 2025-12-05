package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.FriendRequest
import com.runwithme.runwithme.api.entity.FriendRequestStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface FriendRequestRepository : JpaRepository<FriendRequest, UUID> {
    /**
     * Find all friend requests sent by a user
     */
    fun findBySenderId(
        senderId: UUID,
        pageable: Pageable,
    ): Page<FriendRequest>

    /**
     * Find all friend requests received by a user
     */
    fun findByReceiverId(
        receiverId: UUID,
        pageable: Pageable,
    ): Page<FriendRequest>

    /**
     * Find all pending friend requests received by a user
     */
    fun findByReceiverIdAndStatus(
        receiverId: UUID,
        status: FriendRequestStatus,
        pageable: Pageable,
    ): Page<FriendRequest>

    /**
     * Find all pending friend requests sent by a user
     */
    fun findBySenderIdAndStatus(
        senderId: UUID,
        status: FriendRequestStatus,
        pageable: Pageable,
    ): Page<FriendRequest>

    /**
     * Find a specific friend request between two users (in any direction)
     */
    @Query(
        """
        SELECT fr FROM FriendRequest fr 
        WHERE (fr.senderId = :userId1 AND fr.receiverId = :userId2)
           OR (fr.senderId = :userId2 AND fr.receiverId = :userId1)
    """,
    )
    fun findBetweenUsers(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Optional<FriendRequest>

    /**
     * Find a pending friend request between two users
     */
    @Query(
        """
        SELECT fr FROM FriendRequest fr 
        WHERE ((fr.senderId = :userId1 AND fr.receiverId = :userId2)
           OR (fr.senderId = :userId2 AND fr.receiverId = :userId1))
          AND fr.status = :status
    """,
    )
    fun findBetweenUsersWithStatus(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
        @Param("status") status: FriendRequestStatus,
    ): Optional<FriendRequest>

    /**
     * Check if a pending friend request exists between two users
     */
    @Query(
        """
        SELECT COUNT(fr) > 0 FROM FriendRequest fr 
        WHERE ((fr.senderId = :userId1 AND fr.receiverId = :userId2)
           OR (fr.senderId = :userId2 AND fr.receiverId = :userId1))
          AND fr.status = 'PENDING'
    """,
    )
    fun existsPendingRequestBetweenUsers(
        @Param("userId1") userId1: UUID,
        @Param("userId2") userId2: UUID,
    ): Boolean

    /**
     * Count pending requests received by a user
     */
    fun countByReceiverIdAndStatus(
        receiverId: UUID,
        status: FriendRequestStatus,
    ): Long

    /**
     * Count pending requests sent by a user
     */
    fun countBySenderIdAndStatus(
        senderId: UUID,
        status: FriendRequestStatus,
    ): Long
}
