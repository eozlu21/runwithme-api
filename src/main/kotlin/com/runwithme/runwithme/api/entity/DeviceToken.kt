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
 * Enum representing the platform/device type for push notifications.
 */
enum class DevicePlatform {
    IOS,
    ANDROID,
    WEB,
}

/**
 * Entity representing a device token for push notifications.
 *
 * Each user can have multiple device tokens (one per device they use).
 * Tokens are used to send push notifications via Firebase Cloud Messaging (FCM).
 */
@Entity
@Table(
    name = "device_tokens",
    indexes = [
        Index(name = "idx_device_token_user_id", columnList = "user_id"),
        Index(name = "idx_device_token_token", columnList = "token", unique = true),
    ],
)
open class DeviceToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "user_id", nullable = false)
    open var userId: UUID = UUID.randomUUID(),
    @Column(name = "token", nullable = false, unique = true, length = 500)
    open var token: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    open var platform: DevicePlatform = DevicePlatform.ANDROID,
    @Column(name = "device_name", nullable = true)
    open var deviceName: String? = null,
    @Column(name = "is_active", nullable = false)
    open var isActive: Boolean = true,
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "last_used_at", nullable = true)
    open var lastUsedAt: OffsetDateTime? = null,
)
