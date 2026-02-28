package com.worklog.api

import com.worklog.domain.shared.DomainException
import com.worklog.domain.shared.OptimisticLockException
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.http.MockHttpInputMessage
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    private val mockRequest: ServletWebRequest = run {
        val httpRequest = MockHttpServletRequest("GET", "/api/v1/test")
        ServletWebRequest(httpRequest)
    }

    // ---- DomainException ----

    @Test
    fun `handleDomainException returns 404 for NOT_FOUND error code`() {
        val ex = DomainException("USER_NOT_FOUND", "User not found")
        val response = handler.handleDomainException(ex, mockRequest)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("USER_NOT_FOUND", response.body!!.errorCode())
    }

    @Test
    fun `handleDomainException returns 409 for DUPLICATE error code`() {
        val ex = DomainException("DUPLICATE_MEMBER", "Member already exists")
        val response = handler.handleDomainException(ex, mockRequest)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `handleDomainException returns 409 for ALREADY_BOOTSTRAPPED error code`() {
        val ex = DomainException("ALREADY_BOOTSTRAPPED", "Already bootstrapped")
        val response = handler.handleDomainException(ex, mockRequest)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }

    @Test
    fun `handleDomainException returns 422 for state violation error codes`() {
        val stateViolationCodes = listOf(
            "ALREADY_SUBMITTED",
            "ALREADY_APPROVED",
            "ALREADY_REJECTED",
            "NOT_SUBMITTED",
            "NOT_PENDING",
            "INVALID_STATUS_TRANSITION",
            "CANNOT_MODIFY",
            "CANNOT_SUBMIT",
            "CANNOT_APPROVE",
            "CANNOT_REJECT",
            "RECALL_BLOCKED_BY_APPROVAL",
            "REJECT_BLOCKED_BY_APPROVAL",
            "INVALID_TENANT_SELECTION",
        )
        for (code in stateViolationCodes) {
            val ex = DomainException(code, "State violation: $code")
            val response = handler.handleDomainException(ex, mockRequest)
            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.statusCode, "Expected 422 for $code")
        }
    }

    @Test
    fun `handleDomainException returns 400 for generic domain error`() {
        val ex = DomainException("INVALID_INPUT", "Invalid input")
        val response = handler.handleDomainException(ex, mockRequest)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_INPUT", response.body!!.errorCode())
    }

    // ---- OptimisticLockException ----

    @Test
    fun `handleOptimisticLockException returns 409 with version details`() {
        val ex = OptimisticLockException("WorkLog", "abc-123", 1, 2)
        val response = handler.handleOptimisticLockException(ex, mockRequest)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", response.body!!.errorCode())
        val details = response.body!!.details()
        assertEquals("WorkLog", details["aggregateType"])
        assertEquals("abc-123", details["aggregateId"])
        assertEquals(1L, details["expectedVersion"])
        assertEquals(2L, details["actualVersion"])
    }

    // ---- MethodArgumentNotValidException ----

    @Test
    fun `handleValidationException returns 400 with field errors`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "name", "must not be blank"))
        // Use a dummy MethodParameter for the constructor
        val method = GlobalExceptionHandler::class.java.methods.first()
        val param = org.springframework.core.MethodParameter(method, -1)
        val ex = MethodArgumentNotValidException(param, bindingResult)

        val response = handler.handleValidationException(ex, mockRequest)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("VALIDATION_FAILED", response.body!!.errorCode())
        @Suppress("UNCHECKED_CAST")
        val fieldErrors = response.body!!.details()["fieldErrors"] as Map<String, String>
        assertEquals("must not be blank", fieldErrors["name"])
    }

    // ---- MethodArgumentTypeMismatchException ----

    @Test
    fun `handleTypeMismatchException returns 400 with type info`() {
        val method = GlobalExceptionHandler::class.java.methods.first()
        val param = org.springframework.core.MethodParameter(method, -1)
        val ex = MethodArgumentTypeMismatchException(
            "bad-value",
            UUID::class.java,
            "id",
            param,
            IllegalArgumentException("Invalid UUID"),
        )

        val response = handler.handleTypeMismatchException(ex, mockRequest)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_PARAMETER_TYPE", response.body!!.errorCode())
        assertEquals("id", response.body!!.details()["parameter"])
        assertEquals("bad-value", response.body!!.details()["value"])
        assertEquals("UUID", response.body!!.details()["expectedType"])
    }

    // ---- HttpMessageNotReadableException ----

    @Test
    fun `handleHttpMessageNotReadableException returns 400 with generic code`() {
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_REQUEST_BODY", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts MEMBER_ID_REQUIRED code`() {
        val cause = IllegalArgumentException("memberId is required")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("MEMBER_ID_REQUIRED", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts FISCAL_MONTH_REQUIRED code`() {
        val cause = IllegalArgumentException("fiscalMonthStart is required")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("FISCAL_MONTH_REQUIRED", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts REVIEWED_BY_REQUIRED code`() {
        val cause = IllegalArgumentException("reviewedBy is required")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("REVIEWED_BY_REQUIRED", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts REJECTION_REASON_REQUIRED code`() {
        val cause = IllegalArgumentException("rejectionReason is required")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("REJECTION_REASON_REQUIRED", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts REJECTION_REASON_TOO_LONG code`() {
        val cause = IllegalArgumentException("rejectionReason must not exceed 500 characters")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("REJECTION_REASON_TOO_LONG", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException extracts REJECTED_BY_REQUIRED code`() {
        val cause = IllegalArgumentException("rejectedBy is required")
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", cause, inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("REJECTED_BY_REQUIRED", response.body!!.errorCode())
    }

    @Test
    fun `handleHttpMessageNotReadableException uses INVALID_REQUEST_BODY for null rootCause`() {
        val inputMessage = MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Bad JSON", inputMessage)

        val response = handler.handleHttpMessageNotReadableException(ex, mockRequest)

        assertEquals("INVALID_REQUEST_BODY", response.body!!.errorCode())
        assertNotNull(response.body!!.message())
    }

    // ---- IllegalArgumentException ----

    @Test
    fun `handleIllegalArgumentException returns 400`() {
        val ex = IllegalArgumentException("Invalid value provided")

        val response = handler.handleIllegalArgumentException(ex, mockRequest)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("INVALID_ARGUMENT", response.body!!.errorCode())
        assertEquals("Invalid value provided", response.body!!.message())
    }

    // ---- AuthorizationDeniedException ----

    @Test
    fun `handleAuthorizationDeniedException returns 403`() {
        val ex = AuthorizationDeniedException("Access denied")

        val response = handler.handleAuthorizationDeniedException(ex, mockRequest)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("ACCESS_DENIED", response.body!!.errorCode())
        assertTrue(response.body!!.message().contains("permission"))
    }

    // ---- Generic Exception ----

    @Test
    fun `handleGenericException returns 500`() {
        val ex = RuntimeException("Something unexpected")

        val response = handler.handleGenericException(ex, mockRequest)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_SERVER_ERROR", response.body!!.errorCode())
    }

    // ---- Path extraction ----

    @Test
    fun `error response contains correct path`() {
        val ex = DomainException("TEST_ERROR", "test")
        val response = handler.handleDomainException(ex, mockRequest)
        assertEquals("/api/v1/test", response.body!!.details()["path"])
    }
}
