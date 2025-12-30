package com.runwithme.runwithme.api.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

/**
 * Firebase configuration for push notifications.
 *
 * Firebase can be configured in two ways:
 * 1. Using a service account JSON file path (firebase.credentials.path)
 * 2. Using individual credentials properties (firebase.credentials.*)
 *
 * For production, use environment variables or secrets management.
 */
@Configuration
class FirebaseConfig {
    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Value("\${firebase.enabled:false}")
    private var firebaseEnabled: Boolean = false

    @Value("\${firebase.credentials.path:}")
    private var credentialsPath: String = ""

    @Value("\${firebase.credentials.json:}")
    private var credentialsJson: String = ""

    @Value("\${firebase.project-id:}")
    private var projectId: String = ""

    @Bean
    fun firebaseMessaging(): FirebaseMessaging? {
        if (!firebaseEnabled) {
            logger.info("Firebase is disabled. Push notifications will not be sent.")
            return null
        }

        try {
            val firebaseApp = initializeFirebaseApp()
            if (firebaseApp != null) {
                logger.info("Firebase initialized successfully for project: ${firebaseApp.options.projectId}")
                return FirebaseMessaging.getInstance(firebaseApp)
            }
        } catch (e: Exception) {
            logger.error("Failed to initialize Firebase: ${e.message}", e)
        }

        return null
    }

    private fun initializeFirebaseApp(): FirebaseApp? {
        // Check if already initialized
        return try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            // Not initialized yet, proceed with initialization
            val credentials = getCredentials() ?: return null

            val options =
                FirebaseOptions
                    .builder()
                    .setCredentials(credentials)
                    .apply {
                        if (projectId.isNotBlank()) {
                            setProjectId(projectId)
                        }
                    }.build()

            FirebaseApp.initializeApp(options)
        }
    }

    private fun getCredentials(): GoogleCredentials? {
        // Option 1: Load from JSON string (useful for environment variables)
        if (credentialsJson.isNotBlank()) {
            logger.info("Loading Firebase credentials from JSON string")
            return GoogleCredentials.fromStream(
                ByteArrayInputStream(credentialsJson.toByteArray(StandardCharsets.UTF_8)),
            )
        }

        // Option 2: Load from file path
        if (credentialsPath.isNotBlank()) {
            logger.info("Loading Firebase credentials from path: $credentialsPath")
            return if (credentialsPath.startsWith("classpath:")) {
                val resource = ClassPathResource(credentialsPath.removePrefix("classpath:"))
                GoogleCredentials.fromStream(resource.inputStream)
            } else {
                GoogleCredentials.fromStream(FileInputStream(credentialsPath))
            }
        }

        // Option 3: Try default credentials (for GCP environments)
        return try {
            logger.info("Attempting to use default Firebase credentials")
            GoogleCredentials.getApplicationDefault()
        } catch (e: Exception) {
            logger.warn("No Firebase credentials found. Push notifications will be disabled.")
            null
        }
    }
}
