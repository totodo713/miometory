package com.worklog.infrastructure.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/**
 * Redis caching configuration for projection data.
 *
 * Provides caching for:
 * - Monthly calendar projections (5-minute TTL)
 * - Daily totals (5-minute TTL)
 * - Absence totals (5-minute TTL)
 *
 * Cache is automatically invalidated when underlying data changes via cache eviction
 * in the event handlers.
 *
 * Cache names:
 * - calendar:daily-totals - Daily work hour totals by member and date range
 * - calendar:absence-totals - Daily absence hour totals by member and date range
 * - calendar:proxy-dates - Dates with proxy entries by member
 * - calendar:daily-entries - Full daily entry projections
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(
    name = ["worklog.cache.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class CacheConfig {
    companion object {
        const val CACHE_DAILY_TOTALS = "calendar:daily-totals"
        const val CACHE_ABSENCE_TOTALS = "calendar:absence-totals"
        const val CACHE_PROXY_DATES = "calendar:proxy-dates"
        const val CACHE_DAILY_ENTRIES = "calendar:daily-entries"

        // Default TTL for projection caches (5 minutes)
        val DEFAULT_TTL: Duration = Duration.ofMinutes(5)

        // Shorter TTL for frequently changing data
        val SHORT_TTL: Duration = Duration.ofMinutes(2)
    }

    @Bean
    fun cacheManager(redisConnectionFactory: RedisConnectionFactory): CacheManager {
        val keySerializer =
            RedisSerializationContext.SerializationPair
                .fromSerializer(StringRedisSerializer())
        val valueSerializer =
            RedisSerializationContext.SerializationPair
                .fromSerializer(GenericJackson2JsonRedisSerializer())

        val defaultConfig =
            RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                .serializeKeysWith(keySerializer)
                .serializeValuesWith(valueSerializer)
                .disableCachingNullValues()

        // Custom configurations per cache
        val cacheConfigurations =
            mapOf(
                CACHE_DAILY_TOTALS to defaultConfig.entryTtl(DEFAULT_TTL),
                CACHE_ABSENCE_TOTALS to defaultConfig.entryTtl(DEFAULT_TTL),
                CACHE_PROXY_DATES to defaultConfig.entryTtl(DEFAULT_TTL),
                CACHE_DAILY_ENTRIES to defaultConfig.entryTtl(SHORT_TTL),
            )

        return RedisCacheManager
            .builder(redisConnectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .transactionAware()
            .build()
    }
}
