package com.runwithme.runwithme.api.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
@EnableCaching
class CacheConfig(
    @Value("\${spring.data.redis.host:localhost}") private val redisHost: String,
    @Value("\${spring.data.redis.port:6379}") private val redisPort: Int,
    @Value("\${spring.data.redis.password:}") private val redisPassword: String,
) {
    companion object {
        // Cache names (must be const for annotation use)
        const val ROUTE_CACHE = "routes"
        const val USER_PROFILE_CACHE = "userProfiles"
        const val USER_STATISTICS_CACHE = "userStatistics"
    }

    // Conservative TTLs for limited RAM
    private val routeTtl: Duration = Duration.ofMinutes(10)
    private val userProfileTtl: Duration = Duration.ofMinutes(15)
    private val userStatisticsTtl: Duration = Duration.ofMinutes(5)
    private val defaultTtl: Duration = Duration.ofMinutes(5)

    @Bean
    fun redisConnectionFactory(): RedisConnectionFactory {
        val config = RedisStandaloneConfiguration(redisHost, redisPort)
        if (redisPassword.isNotBlank()) {
            config.setPassword(redisPassword)
        }
        return LettuceConnectionFactory(config)
    }

    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        // Configure ObjectMapper with JavaTimeModule and type info as @class property (same as default serializer)
        val objectMapper =
            ObjectMapper().apply {
                registerModule(JavaTimeModule())
                registerModule(KotlinModule.Builder().build())
                activateDefaultTyping(
                    BasicPolymorphicTypeValidator
                        .builder()
                        .allowIfBaseType(Any::class.java)
                        .build(),
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY,
                )
            }
        val jsonSerializer = GenericJackson2JsonRedisSerializer(objectMapper)

        val defaultConfig =
            RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(defaultTtl)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues()

        val cacheConfigurations =
            mapOf(
                ROUTE_CACHE to defaultConfig.entryTtl(routeTtl),
                USER_PROFILE_CACHE to defaultConfig.entryTtl(userProfileTtl),
                USER_STATISTICS_CACHE to defaultConfig.entryTtl(userStatisticsTtl),
            )

        return RedisCacheManager
            .builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build()
    }
}
