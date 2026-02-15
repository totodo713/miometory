package com.worklog.infrastructure.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.StandardCharsets

/**
 * Logging configuration properties.
 *
 * Configurable via application.yml:
 * ```
 * worklog:
 *   logging:
 *     enabled: true
 *     include-request-body: true
 *     include-response-body: false
 *     max-body-length: 1000
 * ```
 */
@Configuration
@ConfigurationProperties(prefix = "worklog.logging")
class LoggingProperties {
    /** Whether request/response logging is enabled. */
    var enabled: Boolean = true

    /** Whether to include request body in logs. */
    var includeRequestBody: Boolean = true

    /** Whether to include response body in logs (can be verbose). */
    var includeResponseBody: Boolean = false

    /** Maximum body length to log (truncated if exceeded). */
    var maxBodyLength: Int = 1000
}

/**
 * Request/Response logging filter with sensitive data masking.
 *
 * Features:
 * - Logs HTTP method, path, status code, and duration
 * - Masks sensitive headers (Authorization, Cookie, Set-Cookie)
 * - Masks sensitive fields in JSON bodies (password, token, secret, etc.)
 * - Configurable body logging with max length truncation
 * - Skips health check endpoints to reduce noise
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10) // Run early in filter chain for comprehensive request logging
class LoggingFilter(private val properties: LoggingProperties) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(LoggingFilter::class.java)

    companion object {
        /** Headers that should be masked in logs. */
        private val SENSITIVE_HEADERS =
            setOf(
                "authorization",
                "cookie",
                "set-cookie",
                "x-api-key",
                "x-auth-token",
                "x-xsrf-token",
            )

        /** JSON field patterns that should be masked. */
        private val SENSITIVE_FIELD_PATTERNS =
            listOf(
                """"password"\s*:\s*"[^"]*"""",
                """"token"\s*:\s*"[^"]*"""",
                """"secret"\s*:\s*"[^"]*"""",
                """"apiKey"\s*:\s*"[^"]*"""",
                """"accessToken"\s*:\s*"[^"]*"""",
                """"refreshToken"\s*:\s*"[^"]*"""",
                """"creditCard"\s*:\s*"[^"]*"""",
                """"ssn"\s*:\s*"[^"]*"""",
            ).map { it.toRegex(RegexOption.IGNORE_CASE) }

        /** Endpoints to skip logging (to reduce noise). */
        private val SKIP_PATHS =
            setOf(
                "/actuator",
                "/health",
                "/api/v1/health",
                "/ready",
            )
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Skip if logging is disabled
        if (!properties.enabled) {
            filterChain.doFilter(request, response)
            return
        }

        // Skip health/actuator endpoints
        val path = request.requestURI
        if (SKIP_PATHS.any { path.startsWith(it) }) {
            filterChain.doFilter(request, response)
            return
        }

        // Wrap request/response for content caching
        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        val startTime = System.currentTimeMillis()

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            logRequest(wrappedRequest, wrappedResponse, duration)

            // Copy response body back to original response
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun logRequest(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        duration: Long,
    ) {
        val method = request.method
        val path = request.requestURI
        val query = request.queryString?.let { "?$it" } ?: ""
        val status = response.status
        val clientIp = getClientIp(request)

        // Build log message
        val logBuilder =
            StringBuilder()
                .append("$method $path$query")
                .append(" | status=$status")
                .append(" | duration=${duration}ms")
                .append(" | client=$clientIp")

        // Add request headers (masked)
        val maskedHeaders = getMaskedHeaders(request)
        if (maskedHeaders.isNotEmpty()) {
            logBuilder.append(" | headers=$maskedHeaders")
        }

        // Add request body if enabled
        if (properties.includeRequestBody && request.contentAsByteArray.isNotEmpty()) {
            val body = getBody(request.contentAsByteArray)
            logBuilder.append(" | requestBody=$body")
        }

        // Add response body if enabled
        if (properties.includeResponseBody && response.contentAsByteArray.isNotEmpty()) {
            val body = getBody(response.contentAsByteArray)
            logBuilder.append(" | responseBody=$body")
        }

        // Log at appropriate level based on status
        val message = logBuilder.toString()
        when {
            status >= 500 -> log.error(message)
            status >= 400 -> log.warn(message)
            else -> log.info(message)
        }
    }

    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrBlank()) {
            xForwardedFor.split(",").first().trim()
        } else {
            request.remoteAddr ?: "unknown"
        }
    }

    private fun getMaskedHeaders(request: HttpServletRequest): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val excludeHeaders = setOf("host", "user-agent", "accept", "content-type", "content-length")
        request.headerNames?.asIterator()?.forEach { name ->
            val lowerName = name.lowercase()
            val value =
                if (lowerName in SENSITIVE_HEADERS) {
                    "[MASKED]"
                } else {
                    request.getHeader(name)
                }
            headers[name] = value
        }
        return headers.filterKeys { it.lowercase() !in excludeHeaders }
    }

    private fun getBody(content: ByteArray): String {
        var body = String(content, StandardCharsets.UTF_8)

        // Mask sensitive fields
        SENSITIVE_FIELD_PATTERNS.forEach { pattern ->
            body =
                body.replace(pattern) { match ->
                    val fieldName = match.value.substringBefore(":").trim()
                    "$fieldName: \"[MASKED]\""
                }
        }

        // Truncate if too long
        if (body.length > properties.maxBodyLength) {
            body = body.take(properties.maxBodyLength) + "...[TRUNCATED]"
        }

        return body
    }
}
