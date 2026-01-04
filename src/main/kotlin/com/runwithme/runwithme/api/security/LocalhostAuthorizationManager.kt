package com.runwithme.runwithme.api.security

import org.slf4j.LoggerFactory
import org.springframework.security.authorization.AuthorizationDecision
import org.springframework.security.authorization.AuthorizationManager
import org.springframework.security.core.Authentication
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import org.springframework.stereotype.Component
import java.util.function.Supplier

/**
 * Authorization manager that restricts access to localhost and Docker network IPs only.
 * Used for admin endpoints that should not require JWT but must only be accessible locally.
 */
@Component
class LocalhostAuthorizationManager : AuthorizationManager<RequestAuthorizationContext> {
    private val logger = LoggerFactory.getLogger(LocalhostAuthorizationManager::class.java)

    private val allowedIpPatterns =
        listOf(
            "127.0.0.1", // IPv4 localhost
            "0:0:0:0:0:0:0:1", // IPv6 localhost (full)
            "::1", // IPv6 localhost (short)
            "172.17.", // Docker bridge network
            "172.18.", // Docker custom networks
            "172.19.", // Docker custom networks
            "172.20.", // Docker custom networks
        )

    override fun check(
        authentication: Supplier<Authentication>?,
        context: RequestAuthorizationContext,
    ): AuthorizationDecision {
        val request = context.request
        val remoteAddr = request.remoteAddr

        val isAllowed =
            allowedIpPatterns.any { pattern ->
                remoteAddr == pattern || remoteAddr.startsWith(pattern)
            }

        logger.debug("Admin endpoint access from IP: $remoteAddr - ${if (isAllowed) "ALLOWED" else "DENIED"}")

        if (!isAllowed) {
            logger.warn("Blocked admin endpoint access from non-local IP: $remoteAddr")
        }

        return AuthorizationDecision(isAllowed)
    }
}
