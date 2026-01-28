package com.worklog.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Rate limiting configuration properties.
 *
 * Configurable via application.yml:
 * ```
 * worklog:
 *   rate-limit:
 *     enabled: true
 *     requests-per-second: 10
 *     burst-size: 20
 * ```
 */
@Configuration
@ConfigurationProperties(prefix = "worklog.rate-limit")
class RateLimitProperties {
    /** Whether rate limiting is enabled. */
    var enabled: Boolean = true

    /** Maximum sustained requests per second per client. */
    var requestsPerSecond: Int = 10

    /** Maximum burst size (token bucket capacity). */
    var burstSize: Int = 20

    /** Cleanup interval for stale buckets (in minutes). */
    var cleanupIntervalMinutes: Int = 5
}

/**
 * Token bucket implementation for rate limiting.
 *
 * Each bucket has a capacity (burst size) and refills at a configured rate.
 * Tokens are consumed on each request; if no tokens are available, the request is rejected.
 */
class TokenBucket(
    private val capacity: Int,
    private val refillRatePerSecond: Int,
) {
    private var tokens: Double = capacity.toDouble()
    private var lastRefillTime: Long = System.nanoTime()

    /**
     * Try to consume one token from the bucket.
     * @return true if a token was available, false if rate limit exceeded
     */
    @Synchronized
    fun tryConsume(): Boolean {
        refill()
        return if (tokens >= 1.0) {
            tokens -= 1.0
            true
        } else {
            false
        }
    }

    private fun refill() {
        val now = System.nanoTime()
        val elapsedNanos = now - lastRefillTime
        val elapsedSeconds = elapsedNanos.toDouble() / TimeUnit.SECONDS.toNanos(1)
        val tokensToAdd = elapsedSeconds * refillRatePerSecond

        tokens = minOf(capacity.toDouble(), tokens + tokensToAdd)
        lastRefillTime = now
    }

    /**
     * Returns true if this bucket has been idle (unused) for the specified duration.
     */
    fun isStale(maxIdleTimeNanos: Long): Boolean = System.nanoTime() - lastRefillTime > maxIdleTimeNanos
}

/**
 * Rate limiting filter using token bucket algorithm.
 *
 * Limits requests per client IP address to prevent abuse and ensure fair usage.
 * Returns HTTP 429 (Too Many Requests) when rate limit is exceeded.
 *
 * Features:
 * - Per-IP rate limiting with configurable requests/second and burst size
 * - Automatic cleanup of stale buckets to prevent memory leaks
 * - Bypass for health check endpoints
 * - Configurable via application properties
 */
@Component
class RateLimitFilter(
    private val properties: RateLimitProperties,
) : OncePerRequestFilter() {
    private val buckets = ConcurrentHashMap<String, TokenBucket>()
    private var lastCleanupTime: Long = System.currentTimeMillis()

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Skip rate limiting if disabled
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        // Skip rate limiting for health check endpoints
        val path = request.requestURI
        if (path.startsWith("/actuator") || path == "/health" || path == "/ready") {
            filterChain.doFilter(request, response)
            return
        }

        // Get client identifier (IP address, considering X-Forwarded-For for proxied requests)
        val clientId = getClientId(request)

        // Get or create token bucket for this client
        val bucket =
            buckets.computeIfAbsent(clientId) {
                TokenBucket(properties.burstSize, properties.requestsPerSecond)
            }

        // Try to consume a token
        if (bucket.tryConsume()) {
            // Request allowed - proceed with filter chain
            cleanupIfNeeded()
            filterChain.doFilter(request, response)
        } else {
            // Rate limit exceeded - return 429
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                """
                {
                    "errorCode": "RATE_LIMIT_EXCEEDED",
                    "message": "Too many requests. Please try again later.",
                    "retryAfterSeconds": 1
                }
                """.trimIndent(),
            )
        }
    }

    /**
     * Extract client identifier from request.
     * Uses X-Forwarded-For header if present (for proxied requests), otherwise uses remote IP.
     */
    private fun getClientId(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            // Take the first IP in the X-Forwarded-For chain (original client)
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr ?: "unknown"
        }
    }

    /**
     * Periodically clean up stale buckets to prevent memory leaks.
     */
    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        val cleanupIntervalMillis = properties.cleanupIntervalMinutes * 60 * 1000L

        if (now - lastCleanupTime > cleanupIntervalMillis) {
            synchronized(this) {
                if (now - lastCleanupTime > cleanupIntervalMillis) {
                    val maxIdleTimeNanos = TimeUnit.MINUTES.toNanos(properties.cleanupIntervalMinutes.toLong())
                    buckets.entries.removeIf { it.value.isStale(maxIdleTimeNanos) }
                    lastCleanupTime = now
                }
            }
        }
    }
}
