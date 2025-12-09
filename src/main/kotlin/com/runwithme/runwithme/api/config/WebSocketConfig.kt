package com.runwithme.runwithme.api.config

import com.runwithme.runwithme.api.security.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.simp.user.DefaultUserDestinationResolver
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.messaging.simp.user.UserDestinationResolver
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.DefaultSimpUserRegistry

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class WebSocketConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userDetailsService: UserDetailsService,
) : WebSocketMessageBrokerConfigurer {
    private val logger = LoggerFactory.getLogger(WebSocketConfig::class.java)

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app")
        config.setUserDestinationPrefix("/user")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(
            object : ChannelInterceptor {
                override fun preSend(
                    message: Message<*>,
                    channel: MessageChannel,
                ): Message<*> {
                    val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

                    if (StompCommand.CONNECT == accessor?.command) {
                        val authHeader = accessor.getFirstNativeHeader("Authorization")
                        logger.info("WebSocket CONNECT - Auth header present: ${authHeader != null}")

                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            val token = authHeader.substring(7)
                            try {
                                val username = jwtTokenProvider.getUsernameFromToken(token)
                                logger.info("WebSocket CONNECT - Username from token: $username")

                                val userDetails = userDetailsService.loadUserByUsername(username)
                                if (jwtTokenProvider.validateToken(token, userDetails)) {
                                    val auth =
                                        UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            userDetails.authorities,
                                        )
                                    accessor.user = auth
                                    logger.info("WebSocket CONNECT - User authenticated: ${auth.name}")
                                } else {
                                    logger.warn("WebSocket CONNECT - Token validation failed for user: $username")
                                }
                            } catch (e: Exception) {
                                logger.error("WebSocket CONNECT - Authentication error: ${e.message}")
                            }
                        }
                    }
                    return message
                }
            },
        )
    }

    @Bean
    fun simpUserRegistry(): SimpUserRegistry = DefaultSimpUserRegistry()

    @Bean
    fun userDestinationResolver(simpUserRegistry: SimpUserRegistry): UserDestinationResolver =
        DefaultUserDestinationResolver(simpUserRegistry)
}
