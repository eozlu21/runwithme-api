package com.runwithme.runwithme.api.service

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import com.runwithme.runwithme.api.dto.MessageNotificationPayload
import com.runwithme.runwithme.api.dto.PushNotificationRequest
import com.runwithme.runwithme.api.dto.PushNotificationResponse
import com.runwithme.runwithme.api.dto.PushNotificationType
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Service for sending push notifications via Firebase Cloud Messaging.
 *
 * This service handles:
 * - Single device notifications
 * - Multi-device notifications (multicast)
 * - Platform-specific configurations (iOS/Android)
 * - Automatic cleanup of invalid tokens
 */
@Service
class PushNotificationService(
    private val firebaseMessaging: FirebaseMessaging?,
    private val deviceTokenService: DeviceTokenService,
) {
    private val logger = LoggerFactory.getLogger(PushNotificationService::class.java)

    companion object {
        private const val MAX_CONTENT_LENGTH = 100
        private const val FCM_BATCH_SIZE = 500 // FCM limit for multicast
    }

    /**
     * Check if push notifications are enabled (Firebase is configured).
     */
    fun isEnabled(): Boolean = firebaseMessaging != null

    /**
     * Send a push notification for a new chat message.
     * This is the main method called when a message is delivered.
     */
    @Async
    fun sendMessageNotification(
        recipientUserId: UUID,
        payload: MessageNotificationPayload,
    ) {
        if (!isEnabled()) {
            logger.debug("Push notifications disabled, skipping message notification")
            return
        }

        val truncatedContent =
            if (payload.content.length > MAX_CONTENT_LENGTH) {
                payload.content.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                payload.content
            }

        val notification =
            PushNotificationRequest(
                recipientUserId = recipientUserId,
                title = payload.senderUsername,
                body = truncatedContent,
                type = PushNotificationType.NEW_MESSAGE,
                data =
                    mapOf(
                        "type" to PushNotificationType.NEW_MESSAGE.name,
                        "messageId" to payload.messageId.toString(),
                        "senderId" to payload.senderId.toString(),
                        "senderUsername" to payload.senderUsername,
                        "conversationId" to payload.conversationId,
                        "click_action" to "OPEN_CHAT",
                    ),
            )

        sendNotification(notification)
    }

    /**
     * Send a push notification for a friend request.
     */
    @Async
    fun sendFriendRequestNotification(
        recipientUserId: UUID,
        senderUsername: String,
        requestId: Long,
    ) {
        if (!isEnabled()) return

        val notification =
            PushNotificationRequest(
                recipientUserId = recipientUserId,
                title = "New Friend Request",
                body = "$senderUsername wants to be your friend",
                type = PushNotificationType.FRIEND_REQUEST,
                data =
                    mapOf(
                        "type" to PushNotificationType.FRIEND_REQUEST.name,
                        "requestId" to requestId.toString(),
                        "senderUsername" to senderUsername,
                        "click_action" to "OPEN_FRIEND_REQUESTS",
                    ),
            )

        sendNotification(notification)
    }

    /**
     * Send a push notification when a friend request is accepted.
     */
    @Async
    fun sendFriendRequestAcceptedNotification(
        recipientUserId: UUID,
        accepterUsername: String,
    ) {
        if (!isEnabled()) return

        val notification =
            PushNotificationRequest(
                recipientUserId = recipientUserId,
                title = "Friend Request Accepted",
                body = "$accepterUsername accepted your friend request",
                type = PushNotificationType.FRIEND_REQUEST_ACCEPTED,
                data =
                    mapOf(
                        "type" to PushNotificationType.FRIEND_REQUEST_ACCEPTED.name,
                        "accepterUsername" to accepterUsername,
                        "click_action" to "OPEN_FRIENDS",
                    ),
            )

        sendNotification(notification)
    }

    /**
     * Send a push notification for a new comment on a post.
     */
    @Async
    fun sendCommentNotification(
        recipientUserId: UUID,
        commenterUsername: String,
        postId: Long,
        commentPreview: String,
    ) {
        if (!isEnabled()) return

        val truncatedComment =
            if (commentPreview.length > MAX_CONTENT_LENGTH) {
                commentPreview.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                commentPreview
            }

        val notification =
            PushNotificationRequest(
                recipientUserId = recipientUserId,
                title = "New Comment",
                body = "$commenterUsername commented: $truncatedComment",
                type = PushNotificationType.NEW_COMMENT,
                data =
                    mapOf(
                        "type" to PushNotificationType.NEW_COMMENT.name,
                        "postId" to postId.toString(),
                        "commenterUsername" to commenterUsername,
                        "click_action" to "OPEN_POST",
                    ),
            )

        sendNotification(notification)
    }

    /**
     * Send a push notification for a new like on a post.
     */
    @Async
    fun sendLikeNotification(
        recipientUserId: UUID,
        likerUsername: String,
        postId: Long,
    ) {
        if (!isEnabled()) return

        val notification =
            PushNotificationRequest(
                recipientUserId = recipientUserId,
                title = "New Like",
                body = "$likerUsername liked your post",
                type = PushNotificationType.NEW_LIKE,
                data =
                    mapOf(
                        "type" to PushNotificationType.NEW_LIKE.name,
                        "postId" to postId.toString(),
                        "likerUsername" to likerUsername,
                        "click_action" to "OPEN_POST",
                    ),
            )

        sendNotification(notification)
    }

    /**
     * Send a generic push notification to a user.
     */
    fun sendNotification(request: PushNotificationRequest): PushNotificationResponse {
        if (!isEnabled()) {
            return PushNotificationResponse(
                success = false,
                deviceCount = 0,
                errorMessage = "Push notifications are not enabled",
            )
        }

        val tokens = deviceTokenService.getActiveTokensForUser(request.recipientUserId)

        if (tokens.isEmpty()) {
            logger.debug("No active device tokens found for user ${request.recipientUserId}")
            return PushNotificationResponse(
                success = true,
                deviceCount = 0,
                errorMessage = "No registered devices",
            )
        }

        return if (tokens.size == 1) {
            sendToSingleDevice(tokens.first(), request)
        } else {
            sendToMultipleDevices(tokens, request)
        }
    }

    private fun sendToSingleDevice(
        token: String,
        request: PushNotificationRequest,
    ): PushNotificationResponse =
        try {
            val message = buildMessage(token, request)
            val response = firebaseMessaging!!.send(message)
            logger.info("Successfully sent notification: $response")

            deviceTokenService.markTokenAsUsed(token)

            PushNotificationResponse(
                success = true,
                deviceCount = 1,
            )
        } catch (e: FirebaseMessagingException) {
            handleFirebaseException(e, listOf(token))
            PushNotificationResponse(
                success = false,
                deviceCount = 0,
                errorMessage = e.message,
            )
        }

    private fun sendToMultipleDevices(
        tokens: List<String>,
        request: PushNotificationRequest,
    ): PushNotificationResponse {
        var totalSuccess = 0
        var totalFailure = 0
        val failedTokens = mutableListOf<String>()

        // FCM has a limit of 500 tokens per multicast request
        tokens.chunked(FCM_BATCH_SIZE).forEach { batch ->
            try {
                val message = buildMulticastMessage(batch, request)
                val response: BatchResponse = firebaseMessaging!!.sendEachForMulticast(message)

                totalSuccess += response.successCount
                totalFailure += response.failureCount

                // Process failed tokens
                response.responses.forEachIndexed { index, sendResponse ->
                    if (!sendResponse.isSuccessful) {
                        failedTokens.add(batch[index])
                        logger.warn(
                            "Failed to send to token: ${sendResponse.exception?.message}",
                        )
                    }
                }

                // Mark successful tokens as used
                batch
                    .filterIndexed { index, _ -> response.responses[index].isSuccessful }
                    .forEach { deviceTokenService.markTokenAsUsed(it) }
            } catch (e: Exception) {
                logger.error("Failed to send multicast notification: ${e.message}", e)
                totalFailure += batch.size
            }
        }

        // Deactivate failed tokens (likely invalid)
        failedTokens.forEach { deviceTokenService.deactivateToken(it) }

        logger.info(
            "Multicast notification sent: $totalSuccess success, $totalFailure failure",
        )

        return PushNotificationResponse(
            success = totalSuccess > 0,
            deviceCount = totalSuccess,
            errorMessage = if (totalFailure > 0) "$totalFailure devices failed" else null,
        )
    }

    private fun buildMessage(
        token: String,
        request: PushNotificationRequest,
    ): Message =
        Message
            .builder()
            .setToken(token)
            .setNotification(buildNotification(request))
            .putAllData(request.data)
            .setAndroidConfig(buildAndroidConfig(request))
            .setApnsConfig(buildApnsConfig(request))
            .build()

    private fun buildMulticastMessage(
        tokens: List<String>,
        request: PushNotificationRequest,
    ): MulticastMessage =
        MulticastMessage
            .builder()
            .addAllTokens(tokens)
            .setNotification(buildNotification(request))
            .putAllData(request.data)
            .setAndroidConfig(buildAndroidConfig(request))
            .setApnsConfig(buildApnsConfig(request))
            .build()

    private fun buildNotification(request: PushNotificationRequest): Notification =
        Notification
            .builder()
            .setTitle(request.title)
            .setBody(request.body)
            .apply {
                request.imageUrl?.let { setImage(it) }
            }.build()

    private fun buildAndroidConfig(request: PushNotificationRequest): AndroidConfig =
        AndroidConfig
            .builder()
            .setPriority(AndroidConfig.Priority.HIGH)
            .setNotification(
                AndroidNotification
                    .builder()
                    .setTitle(request.title)
                    .setBody(request.body)
                    .setChannelId(getAndroidChannelId(request.type))
                    .setIcon("ic_notification")
                    .setColor("#FF6B00") // Orange color for RunWithMe
                    .setClickAction(request.data["click_action"] ?: "OPEN_APP")
                    .build(),
            ).build()

    private fun buildApnsConfig(request: PushNotificationRequest): ApnsConfig =
        ApnsConfig
            .builder()
            .setAps(
                Aps
                    .builder()
                    .setAlert(
                        com.google.firebase.messaging.ApsAlert
                            .builder()
                            .setTitle(request.title)
                            .setBody(request.body)
                            .build(),
                    ).setSound("default")
                    .setBadge(1)
                    .setCategory(getIosCategory(request.type))
                    .build(),
            ).build()

    private fun getAndroidChannelId(type: PushNotificationType): String =
        when (type) {
            PushNotificationType.NEW_MESSAGE -> "messages"
            PushNotificationType.FRIEND_REQUEST,
            PushNotificationType.FRIEND_REQUEST_ACCEPTED,
            -> "social"
            PushNotificationType.NEW_COMMENT,
            PushNotificationType.NEW_LIKE,
            -> "activity"
            PushNotificationType.RUN_INVITATION -> "runs"
            PushNotificationType.GENERAL -> "general"
        }

    private fun getIosCategory(type: PushNotificationType): String =
        when (type) {
            PushNotificationType.NEW_MESSAGE -> "MESSAGE_CATEGORY"
            PushNotificationType.FRIEND_REQUEST -> "FRIEND_REQUEST_CATEGORY"
            else -> "DEFAULT_CATEGORY"
        }

    private fun handleFirebaseException(
        exception: FirebaseMessagingException,
        tokens: List<String>,
    ) {
        logger.error("Firebase messaging error: ${exception.messagingErrorCode} - ${exception.message}")

        // Handle specific error codes
        when (exception.messagingErrorCode?.name) {
            "UNREGISTERED", "INVALID_ARGUMENT" -> {
                // Token is no longer valid, deactivate it
                tokens.forEach { deviceTokenService.deactivateToken(it) }
            }
            else -> {
                // Log other errors for investigation
                logger.warn("Unhandled Firebase error code: ${exception.messagingErrorCode}")
            }
        }
    }
}
