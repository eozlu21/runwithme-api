package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "messages")
open class Message(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "sender_id", nullable = false)
    open var senderId: Long? = null,
    @Column(name = "recipient_id", nullable = false)
    open var recipientId: Long? = null,
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    open var content: String? = null,
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "is_read", nullable = false)
    open var isRead: Boolean = false,
)
