package com.runwithme.runwithme.api.config

import com.runwithme.runwithme.api.security.JwtTokenProvider
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
class WebSocketConfig(
    private val jwtTokenProvider: JwtTokenProvider,
    private val userDetailsService: UserDetailsService,
) : WebSocketMessageBrokerConfigurer {
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
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            val token = authHeader.substring(7)
                            try {
                                val username = jwtTokenProvider.getUsernameFromToken(token)
                                val userDetails = userDetailsService.loadUserByUsername(username)
                                if (jwtTokenProvider.validateToken(token, userDetails)) {
                                    val auth =
                                        UsernamePasswordAuthenticationToken(
                                            userDetails,
                                            null,
                                            userDetails.authorities,
                                        )
                                    accessor.user = auth
                                }
                            } catch (e: Exception) {
                                // Token validation failed
                            }
                        }
                    }
                    return message
                }
            },
        )
    }
}
