package com.worklog.infrastructure.config

import com.worklog.application.audit.AuditLogService
import com.worklog.domain.user.UserId
import jakarta.servlet.http.HttpServletRequest
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.util.UUID

/**
 * AOP aspect that captures all admin controller actions for audit logging.
 *
 * Intercepts any public method in controllers matching `com.worklog.api.Admin*Controller`
 * or `DailyApprovalController`, and logs the action (method name), target entity,
 * and request details to the audit_logs table.
 */
@Aspect
@Component
class AdminAuditAspect(private val auditLogService: AuditLogService) {
    private val log = LoggerFactory.getLogger(AdminAuditAspect::class.java)

    @Around(
        "execution(* com.worklog.api.Admin*Controller.*(..)) || " +
            "execution(* com.worklog.api.DailyApprovalController.*(..))",
    )
    fun auditAdminAction(joinPoint: ProceedingJoinPoint): Any? {
        var error: Throwable? = null
        var result: Any? = null

        try {
            result = joinPoint.proceed()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            error = e
        }

        try {
            val authentication = SecurityContextHolder.getContext().authentication
            val email = authentication?.name
            val userId = resolveUserId(email)
            val ipAddress = getClientIpAddress()

            val controllerName = joinPoint.target.javaClass.simpleName
            val methodName = joinPoint.signature.name
            val eventType = "ADMIN_ACTION"
            val status = if (error == null) "SUCCESS" else "FAILURE"

            val details = buildString {
                append("""{"controller":"""")
                append(escapeJson(controllerName))
                append("""","method":"""")
                append(escapeJson(methodName))
                append("""","status":"""")
                append(status)
                append('"')
                if (email != null) {
                    append(""","actor":"""")
                    append(escapeJson(email))
                    append('"')
                }
                if (error != null) {
                    append(""","error":"""")
                    append(escapeJson(error.message ?: "Unknown error"))
                    append('"')
                }
                append("}")
            }

            auditLogService.logEvent(userId, eventType, ipAddress, details)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.warn("Failed to log admin audit event: {}", e.message)
        }

        if (error != null) {
            throw error
        }

        return result
    }

    private fun escapeJson(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    private fun resolveUserId(email: String?): UserId? {
        if (email == null) return null
        return try {
            // Try to parse the authentication name as a UUID first (for cases where it's stored as UUID)
            UserId.of(UUID.fromString(email))
        } catch (_: IllegalArgumentException) {
            // If not a UUID, it's an email — return null and let the audit log use email in details
            null
        }
    }

    private fun getClientIpAddress(): String? {
        val requestAttributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val request: HttpServletRequest = requestAttributes?.request ?: return null
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").first().trim()
        } else {
            request.remoteAddr
        }
    }
}
