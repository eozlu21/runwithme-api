package com.runwithme.runwithme.api.controller

import com.runwithme.runwithme.api.dto.DeviceListResponse
import com.runwithme.runwithme.api.dto.DeviceTokenResponse
import com.runwithme.runwithme.api.dto.RegisterDeviceTokenRequest
import com.runwithme.runwithme.api.dto.UnregisterDeviceTokenRequest
import com.runwithme.runwithme.api.service.DeviceTokenService
import com.runwithme.runwithme.api.service.PushNotificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(
    name = "Push Notifications",
    description = """
    Device registration APIs for push notifications.
    
    **Setup Flow:**
    1. Get FCM token from Firebase SDK in your mobile app
    2. Call POST /api/v1/notifications/devices to register the token
    3. The server will automatically send push notifications for:
       - New chat messages (when recipient is offline)
       - Friend requests
       - Comments and likes on your posts
    
    **Important:**
    - Register the device token after user login
    - Update the token when Firebase refreshes it (onTokenRefresh)
    - Unregister the token on user logout
    
    **Platform-specific setup:**
    - **Android:** Use Firebase Cloud Messaging SDK
    - **iOS:** Use Firebase Cloud Messaging SDK with APNs
    - **Web:** Use Firebase Cloud Messaging for web
    """,
)
class PushNotificationController(
    private val deviceTokenService: DeviceTokenService,
    private val pushNotificationService: PushNotificationService,
) {
    @PostMapping("/devices")
    @Operation(
        summary = "Register device for push notifications",
        description = """
            Register or update a device token for receiving push notifications.
            
            Call this endpoint:
            - After successful user login
            - When Firebase refreshes the token (onTokenRefresh callback)
            
            If the token already exists for another user, it will be reassigned to the current user.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Device registered successfully",
                content = [
                    Content(
                        schema = Schema(implementation = DeviceTokenResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun registerDevice(
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
        authentication: Authentication,
    ): ResponseEntity<DeviceTokenResponse> {
        val response = deviceTokenService.registerDeviceToken(authentication.name, request)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/devices")
    @Operation(
        summary = "Unregister device from push notifications",
        description = """
            Remove a device token to stop receiving push notifications.
            
            Call this endpoint:
            - When user logs out
            - When user disables notifications in app settings
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Device unregistered successfully",
            ),
            ApiResponse(responseCode = "400", description = "Invalid request"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "Device token not found"),
        ],
    )
    fun unregisterDevice(
        @Valid @RequestBody request: UnregisterDeviceTokenRequest,
        authentication: Authentication,
    ): ResponseEntity<Void> {
        val success = deviceTokenService.unregisterDeviceToken(authentication.name, request.token)
        return if (success) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/devices")
    @Operation(
        summary = "Get all registered devices",
        description = "Returns a list of all devices registered for push notifications for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "List of registered devices",
                content = [
                    Content(
                        schema = Schema(implementation = DeviceListResponse::class),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun getDevices(authentication: Authentication): ResponseEntity<DeviceListResponse> {
        val response = deviceTokenService.getDevices(authentication.name)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/devices/all")
    @Operation(
        summary = "Unregister all devices",
        description = """
            Deactivate all device tokens for the current user.
            Useful when user wants to log out from all devices.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "All devices deactivated",
                content = [
                    Content(
                        schema =
                            Schema(
                                implementation = Map::class,
                                example = """{"deactivatedCount": 3}""",
                            ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
        ],
    )
    fun unregisterAllDevices(authentication: Authentication): ResponseEntity<Map<String, Int>> {
        val count = deviceTokenService.deactivateAllTokens(authentication.name)
        return ResponseEntity.ok(mapOf("deactivatedCount" to count))
    }

    @GetMapping("/status")
    @Operation(
        summary = "Check push notification status",
        description = "Returns whether push notifications are enabled on the server.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Push notification status",
            ),
        ],
    )
    fun getStatus(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf(
                "enabled" to pushNotificationService.isEnabled(),
                "provider" to "Firebase Cloud Messaging",
            ),
        )
}
