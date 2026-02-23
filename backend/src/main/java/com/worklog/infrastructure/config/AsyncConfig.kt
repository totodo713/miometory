package com.worklog.infrastructure.config

import org.slf4j.LoggerFactory
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method

@Configuration
@Profile("!test")
class AsyncConfig : AsyncConfigurer {

    private val log = LoggerFactory.getLogger(AsyncConfig::class.java)

    @Bean("applicationTaskExecutor")
    override fun getAsyncExecutor(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 4
        executor.setQueueCapacity(100)
        executor.setThreadNamePrefix("async-audit-")
        executor.initialize()
        return executor
    }

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler = AsyncUncaughtExceptionHandler {
            ex: Throwable,
            method: Method,
            params: Array<out Any>,
        ->
        log.error(
            "Uncaught async exception in {}.{}(): {}",
            method.declaringClass.simpleName,
            method.name,
            ex.message,
            ex,
        )
    }
}
