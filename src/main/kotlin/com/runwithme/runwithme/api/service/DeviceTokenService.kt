package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.DeviceListResponse
import com.runwithme.runwithme.api.dto.DeviceTokenResponse
import com.runwithme.runwithme.api.dto.RegisterDeviceTokenRequest
import com.runwithme.runwithme.api.entity.DeviceToken
import com.runwithme.runwithme.api.repository.DeviceTokenRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Service for managing device tokens used for push notifications.
 */
@Service
class DeviceTokenService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val userRepository: UserRepository,
) {
    private val logger = LoggerFactory.getLogger(DeviceTokenService::class.java)

    /**
     * Register a new device token for a user.
     * If the token already exists, it will be updated and reactivated.
     */
    @Transactional
    fun registerDeviceToken(
        username: String,
        request: RegisterDeviceTokenRequest,
    ): DeviceTokenResponse {
        val user =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val userId = user.userId!!

        // Check if token already exists
        val existingToken = deviceTokenRepository.findByToken(request.token)

        val deviceToken =
            if (existingToken.isPresent) {
                // Token exists - update it
                val token = existingToken.get()

                // If token belongs to different user, reassign it
                if (token.userId != userId) {
                    logger.info("Reassigning device token from user ${token.userId} to user $userId")
                }

                token.apply {
                    this.userId = userId
                    this.platform = request.platform
                    this.deviceName = request.deviceName
                    this.isActive = true
                    this.updatedAt = OffsetDateTime.now()
                }
                deviceTokenRepository.save(token)
            } else {
                // Create new token
                val newToken =
                    DeviceToken(
                        userId = userId,
                        token = request.token,
                        platform = request.platform,
                        deviceName = request.deviceName,
                        isActive = true,
                    )
                deviceTokenRepository.save(newToken)
            }

        logger.info("Registered device token for user $username (platform: ${request.platform})")
        return DeviceTokenResponse.fromEntity(deviceToken)
    }

    /**
     * Unregister a device token.
     */
    @Transactional
    fun unregisterDeviceToken(
        username: String,
        token: String,
    ): Boolean {
        val user =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val deviceToken = deviceTokenRepository.findByUserIdAndToken(user.userId!!, token)

        return if (deviceToken.isPresent) {
            deviceTokenRepository.delete(deviceToken.get())
            logger.info("Unregistered device token for user $username")
            true
        } else {
            logger.warn("Device token not found for user $username")
            false
        }
    }

    /**
     * Get all registered devices for a user.
     */
    @Transactional(readOnly = true)
    fun getDevices(username: String): DeviceListResponse {
        val user =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val devices = deviceTokenRepository.findByUserId(user.userId!!)
        val deviceResponses = devices.map { DeviceTokenResponse.fromEntity(it) }
        val activeCount = devices.count { it.isActive }

        return DeviceListResponse(
            devices = deviceResponses,
            activeCount = activeCount,
        )
    }

    /**
     * Get all active device tokens for a user by their ID.
     */
    @Transactional(readOnly = true)
    fun getActiveTokensForUser(userId: UUID): List<String> =
        deviceTokenRepository
            .findByUserIdAndIsActiveTrue(userId)
            .map { it.token }

    /**
     * Deactivate all device tokens for a user (e.g., on logout from all devices).
     */
    @Transactional
    fun deactivateAllTokens(username: String): Int {
        val user =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val count = deviceTokenRepository.countByUserIdAndIsActiveTrue(user.userId!!).toInt()
        deviceTokenRepository.deactivateAllByUserId(user.userId!!)
        logger.info("Deactivated $count device tokens for user $username")
        return count
    }

    /**
     * Mark a token as used (updates lastUsedAt timestamp).
     */
    @Transactional
    fun markTokenAsUsed(token: String) {
        deviceTokenRepository.findByToken(token).ifPresent {
            it.lastUsedAt = OffsetDateTime.now()
            deviceTokenRepository.save(it)
        }
    }

    /**
     * Deactivate a specific token (e.g., when FCM reports it as invalid).
     */
    @Transactional
    fun deactivateToken(token: String) {
        deviceTokenRepository.findByToken(token).ifPresent {
            it.isActive = false
            it.updatedAt = OffsetDateTime.now()
            deviceTokenRepository.save(it)
            logger.info("Deactivated invalid device token")
        }
    }
}
