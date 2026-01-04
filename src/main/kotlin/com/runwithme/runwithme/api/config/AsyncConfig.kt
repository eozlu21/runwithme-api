package com.runwithme.runwithme.api.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor

/**
 * Configuration for async task execution.
 * Used for background tasks like sending push notifications.
 */
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    private val logger = LoggerFactory.getLogger(AsyncConfig::class.java)

    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("async-push-")
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler =
        AsyncUncaughtExceptionHandler { ex: Throwable, method: Method, _: Array<Any> ->
            logger.error("Async exception in method ${method.name}: ${ex.message}", ex)
        }
}
