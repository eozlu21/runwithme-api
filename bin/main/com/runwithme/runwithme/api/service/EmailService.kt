package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.entity.EmailVerificationToken
import com.runwithme.runwithme.api.entity.User
import com.runwithme.runwithme.api.repository.EmailVerificationTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    private val tokenRepository: EmailVerificationTokenRepository,
    @Value("\${app.base-url:http://localhost:8080}") private val baseUrl: String,
    @Value("\${spring.mail.username:noreply@runwithme.com}") private val fromEmail: String,
) {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    companion object {
        private const val TOKEN_VALIDITY_HOURS = 24L
    }

    @Transactional
    fun createVerificationToken(user: User): EmailVerificationToken {
        // Delete any existing tokens for this user
        tokenRepository.deleteByUser(user)

        val token =
            EmailVerificationToken(
                token = UUID.randomUUID().toString(),
                user = user,
                expiresAt = OffsetDateTime.now().plusHours(TOKEN_VALIDITY_HOURS),
                createdAt = OffsetDateTime.now(),
            )

        return tokenRepository.save(token)
    }

    fun sendVerificationEmail(
        user: User,
        token: String,
    ) {
        val verificationUrl = "$baseUrl/api/v1/auth/verify-email?token=$token"

        val message =
            SimpleMailMessage().apply {
                from = fromEmail
                setTo(user.email)
                subject = "RunWithMe - Verify Your Email"
                text =
                    """
                    Hi ${user.username},
                    
                    Welcome to RunWithMe! Please verify your email address by clicking the link below:
                    
                    $verificationUrl
                    
                    This link will expire in $TOKEN_VALIDITY_HOURS hours.
                    
                    If you didn't create an account with RunWithMe, please ignore this email.
                    
                    Best regards,
                    The RunWithMe Team
                    """.trimIndent()
            }

        try {
            mailSender.send(message)
            logger.info("Verification email sent to ${user.email}")
        } catch (e: Exception) {
            logger.error("Failed to send verification email to ${user.email}: ${e.message}")
            throw RuntimeException("Failed to send verification email. Please try again later.")
        }
    }

    fun findByToken(token: String): EmailVerificationToken? = tokenRepository.findByToken(token).orElse(null)

    @Transactional
    fun deleteToken(token: EmailVerificationToken) {
        tokenRepository.delete(token)
    }
}
