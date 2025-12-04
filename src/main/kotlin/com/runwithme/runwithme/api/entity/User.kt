package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "users")
open class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    open var userId: UUID? = null,
    @Column(name = "username", nullable = false) open var username: String = "",
    @Column(name = "email", nullable = false) open var email: String = "",
    @Column(name = "password_hash", nullable = false) open var passwordHash: String = "",
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "email_verified", nullable = false) open var emailVerified: Boolean = false,
)
