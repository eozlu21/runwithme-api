package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.EmailVerificationToken
import com.runwithme.runwithme.api.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, Long> {
    fun findByToken(token: String): Optional<EmailVerificationToken>

    fun findByUser(user: User): Optional<EmailVerificationToken>

    fun deleteByUser(user: User)
}
