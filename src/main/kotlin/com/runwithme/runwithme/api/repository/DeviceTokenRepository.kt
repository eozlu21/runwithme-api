package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.DevicePlatform
import com.runwithme.runwithme.api.entity.DeviceToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    /**
     * Find all active device tokens for a user.
     */
    fun findByUserIdAndIsActiveTrue(userId: UUID): List<DeviceToken>

    /**
     * Find a device token by its token string.
     */
    fun findByToken(token: String): Optional<DeviceToken>

    /**
     * Find a device token for a specific user and token string.
     */
    fun findByUserIdAndToken(
        userId: UUID,
        token: String,
    ): Optional<DeviceToken>

    /**
     * Find all device tokens for a user.
     */
    fun findByUserId(userId: UUID): List<DeviceToken>

    /**
     * Delete all device tokens for a user.
     */
    @Modifying
    @Query("DELETE FROM DeviceToken dt WHERE dt.userId = :userId")
    fun deleteByUserId(
        @Param("userId") userId: UUID,
    )

    /**
     * Delete a specific device token.
     */
    @Modifying
    @Query("DELETE FROM DeviceToken dt WHERE dt.token = :token")
    fun deleteByToken(
        @Param("token") token: String,
    )

    /**
     * Deactivate all tokens for a user (useful for logout from all devices).
     */
    @Modifying
    @Query("UPDATE DeviceToken dt SET dt.isActive = false WHERE dt.userId = :userId")
    fun deactivateAllByUserId(
        @Param("userId") userId: UUID,
    )

    /**
     * Count active device tokens for a user.
     */
    fun countByUserIdAndIsActiveTrue(userId: UUID): Long

    /**
     * Find active tokens by user ID and platform.
     */
    fun findByUserIdAndPlatformAndIsActiveTrue(
        userId: UUID,
        platform: DevicePlatform,
    ): List<DeviceToken>
}
