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
 * Helper class to check if an IP address is from a trusted proxy.
 * Supports both exact IP addresses and CIDR notation.
 */
class TrustedProxyChecker(trustedProxiesConfig: String) {
    private val trustedAddresses = mutableSetOf<String>()
    private val trustedCidrs = mutableListOf<CidrRange>()
    
    init {
        trustedProxiesConfig.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            if (entry.contains("/")) {
                // CIDR notation
                try {
                    trustedCidrs.add(CidrRange.parse(entry))
                } catch (e: Exception) {
                    // Log and skip invalid CIDR
                }
            } else {
                // Exact IP address
                trustedAddresses.add(entry)
            }
        }
    }
    
    fun isTrusted(ipAddress: String): Boolean {
        // Check exact match first
        if (trustedAddresses.contains(ipAddress)) {
            return true
        }
        
        // Check CIDR ranges
        return trustedCidrs.any { it.contains(ipAddress) }
    }
    
    /**
     * Simple CIDR range representation for IPv4.
     */
    private data class CidrRange(val networkAddress: Long, val mask: Long) {
        fun contains(ipAddress: String): Boolean {
            return try {
                val ip = ipToLong(ipAddress)
                (ip and mask) == networkAddress
            } catch (e: Exception) {
                false
            }
        }
        
        companion object {
            fun parse(cidr: String): CidrRange {
                val parts = cidr.split("/")
                val ip = ipToLong(parts[0])
                val prefixLength = parts[1].toInt()
                val mask = if (prefixLength == 0) 0L else (-1L shl (32 - prefixLength)) and 0xFFFFFFFFL
                val network = ip and mask
                return CidrRange(network, mask)
            }
            
            private fun ipToLong(ip: String): Long {
                val octets = ip.split(".")
                if (octets.size != 4) throw IllegalArgumentException("Invalid IPv4 address: $ip")
                return octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
            }
        }
    }
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
    
    // Lazy-initialized set of trusted proxy addresses/ranges
    private val trustedProxyChecker: TrustedProxyChecker by lazy {
        TrustedProxyChecker(properties.trustedProxies)
    }

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
        if (path.startsWith("/actuator") || path == "/health" || path == "/api/v1/health" || path == "/ready") {
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

        // Always run cleanup regardless of allow/deny to prevent memory pressure
        // from sustained over-limit traffic or spoofed X-Forwarded-For values
        cleanupIfNeeded()

        // Try to consume a token
        if (bucket.tryConsume()) {
            // Request allowed - proceed with filter chain
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
     * Only trusts X-Forwarded-For header when the request comes from a known trusted proxy.
     * This prevents rate limit bypass via header spoofing.
     */
    private fun getClientId(request: HttpServletRequest): String {
        val remoteAddr = request.remoteAddr ?: "unknown"
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        
        // Only trust X-Forwarded-For if request comes from a trusted proxy
        return if (!xForwardedFor.isNullOrBlank() && trustedProxyChecker.isTrusted(remoteAddr)) {
            // Take the first IP in the X-Forwarded-For chain (original client)
            xForwardedFor.split(",").first().trim()
        } else {
            remoteAddr
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
 *     trusted-proxies: "127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16"
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

    /** 
     * Comma-separated list of trusted proxy IP addresses or CIDR ranges.
     * X-Forwarded-For header is only trusted from these IPs.
     * Default includes loopback and private network ranges.
     */
    var trustedProxies: String = "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16"
}
