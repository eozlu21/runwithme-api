package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Entity representing a friend request between two users.
 * A friend request is created when one user wants to add another as a friend.
 */
@Entity
@Table(
    name = "friend_requests",
    indexes = [
        Index(name = "idx_friend_request_sender", columnList = "sender_id"),
        Index(name = "idx_friend_request_receiver", columnList = "receiver_id"),
        Index(name = "idx_friend_request_status", columnList = "status"),
    ],
)
open class FriendRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    open var requestId: UUID? = null,
    @Column(name = "sender_id", nullable = false)
    open var senderId: UUID = UUID.randomUUID(),
    @Column(name = "receiver_id", nullable = false)
    open var receiverId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: FriendRequestStatus = FriendRequestStatus.PENDING,
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "message")
    open var message: String? = null,
)
