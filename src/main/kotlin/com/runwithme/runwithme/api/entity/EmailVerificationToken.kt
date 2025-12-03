package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime

@Entity
@Table(name = "email_verification_tokens")
open class EmailVerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    open var id: Long? = null,
    @Column(name = "token", nullable = false, unique = true) open var token: String = "",
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    open var user: User? = null,
    @Column(name = "expires_at", nullable = false)
    open var expiresAt: OffsetDateTime = OffsetDateTime.now(),
    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
) {
    fun isExpired(): Boolean = OffsetDateTime.now().isAfter(expiresAt)
}
