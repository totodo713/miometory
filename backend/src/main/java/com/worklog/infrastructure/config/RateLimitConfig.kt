package com.worklog.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(TrustedProxyChecker::class.java)
    private val trustedAddresses = mutableSetOf<String>()
    private val trustedCidrs = mutableListOf<CidrRange>()

    init {
        trustedProxiesConfig.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            if (entry.contains("/")) {
                // CIDR notation
                try {
                    trustedCidrs.add(CidrRange.parse(entry))
                } catch (e: NumberFormatException) {
                    logger.warn("Invalid CIDR notation, skipping: {}", entry, e)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Invalid CIDR notation, skipping: {}", entry, e)
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
        fun contains(ipAddress: String): Boolean = try {
            val ip = ipToLong(ipAddress)
            (ip and mask) == networkAddress
        } catch (e: NumberFormatException) {
            logger.warn("Failed to check IP against CIDR range: {}", ipAddress, e)
            false
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to check IP against CIDR range: {}", ipAddress, e)
            false
        }

        companion object {
            private val logger = LoggerFactory.getLogger(CidrRange::class.java)
            private const val IPV4_FULL_MASK = 0xFFFFFFFFL
            private const val IPV4_BITS = 32
            private const val BITS_PER_OCTET = 8

            fun parse(cidr: String): CidrRange {
                val parts = cidr.split("/")
                val ip = ipToLong(parts[0])
                val prefixLength = parts[1].toInt()
                val mask = if (prefixLength == 0) 0L else (-1L shl (IPV4_BITS - prefixLength)) and IPV4_FULL_MASK
                val network = ip and mask
                return CidrRange(network, mask)
            }

            private fun ipToLong(ip: String): Long {
                val octets = ip.split(".")
                if (octets.size != 4) throw IllegalArgumentException("Invalid IPv4 address: $ip")
                return octets.fold(0L) { acc, octet -> (acc shl BITS_PER_OCTET) or octet.toLong() }
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
class TokenBucket(private val capacity: Int, private val refillRatePerSecond: Int) {
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
 * - Path-specific rate limits (e.g., stricter limits for /auth endpoints)
 * - Automatic cleanup of stale buckets to prevent memory leaks
 * - Bypass for health check endpoints
 * - Configurable via application properties
 */
@Component
class RateLimitFilter(private val properties: RateLimitProperties) : OncePerRequestFilter() {
    companion object {
        private const val MILLIS_PER_SECOND = 1000L
        private const val SECONDS_PER_MINUTE = 60
        private val HEALTH_CHECK_PATHS = listOf("/health", "/api/v1/health", "/ready")
    }

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
        if (path.startsWith("/actuator") || path in HEALTH_CHECK_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        // Get client identifier (IP address, considering X-Forwarded-For for proxied requests)
        val clientId = getClientId(request)

        // Determine rate limit based on path (stricter for auth endpoints)
        val (rps, burst) = getRateLimitForPath(path)

        // Get or create token bucket for this client (path-specific)
        // Use path prefix in bucket key to maintain separate limits per path type
        val bucketKey = if (isAuthPath(path)) "auth:$clientId" else "default:$clientId"
        val bucket =
            buckets.computeIfAbsent(bucketKey) {
                TokenBucket(burst, rps)
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
     * Determine if path is an authentication endpoint requiring stricter rate limiting.
     */
    private fun isAuthPath(path: String): Boolean = path == "/api/v1/auth" ||
        path.startsWith("/api/v1/auth/") ||
        path == "/auth" ||
        path.startsWith("/auth/")

    /**
     * Get rate limit parameters for the given path.
     * Returns (requestsPerSecond, burstSize) tuple.
     */
    private fun getRateLimitForPath(path: String): Pair<Int, Int> = if (isAuthPath(path)) {
        // Stricter limits for auth endpoints to prevent brute force attacks
        Pair(properties.authRequestsPerSecond, properties.authBurstSize)
    } else {
        // Default limits for other endpoints
        Pair(properties.requestsPerSecond, properties.burstSize)
    }

    /**
     * Periodically clean up stale buckets to prevent memory leaks.
     */
    private fun cleanupIfNeeded() {
        val now = System.currentTimeMillis()
        val cleanupIntervalMillis = properties.cleanupIntervalMinutes * SECONDS_PER_MINUTE * MILLIS_PER_SECOND

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
 *     auth-requests-per-second: 3
 *     auth-burst-size: 5
 *     trusted-proxies: "127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16"
 * ```
 */
@Configuration
@ConfigurationProperties(prefix = "worklog.rate-limit")
class RateLimitProperties {
    /** Whether rate limiting is enabled. */
    var enabled: Boolean = true

    /** Maximum sustained requests per second per client (default endpoints). */
    var requestsPerSecond: Int = 10

    /** Maximum burst size (token bucket capacity) for default endpoints. */
    var burstSize: Int = 20

    /** Maximum sustained requests per second per client (auth endpoints). */
    var authRequestsPerSecond: Int = 3

    /** Maximum burst size for auth endpoints (stricter to prevent brute force). */
    var authBurstSize: Int = 5

    /** Cleanup interval for stale buckets (in minutes). */
    var cleanupIntervalMinutes: Int = 5

    /**
     * Comma-separated list of trusted proxy IP addresses or CIDR ranges.
     * X-Forwarded-For header is only trusted from these IPs.
     * Default includes loopback and private network ranges.
     */
    var trustedProxies: String = "127.0.0.1,::1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16"
}
