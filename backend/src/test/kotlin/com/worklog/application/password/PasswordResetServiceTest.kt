package com.worklog.application.password

import com.worklog.domain.password.PasswordResetToken
import com.worklog.domain.role.RoleId
import com.worklog.domain.user.User
import com.worklog.domain.user.UserId
import com.worklog.infrastructure.email.EmailService
import com.worklog.infrastructure.persistence.JdbcUserRepository
import com.worklog.infrastructure.persistence.JdbcUserSessionRepository
import com.worklog.infrastructure.persistence.PasswordResetTokenRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.argThat
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class PasswordResetServiceTest {
    @Mock
    private lateinit var userRepository: JdbcUserRepository

    @Mock
    private lateinit var tokenRepository: PasswordResetTokenRepository

    @Mock
    private lateinit var emailService: EmailService

    @Mock
    private lateinit var userSessionRepository: JdbcUserSessionRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var service: PasswordResetService

    @BeforeEach
    fun setup() {
        service = PasswordResetService(userRepository, tokenRepository, emailService, userSessionRepository, passwordEncoder)
    }

    @Test
    fun `requestReset should do nothing for non-existent user`() {
        `when`(userRepository.findByEmail(anyString())).thenReturn(Optional.empty())
        service.requestReset("notfound@example.com")
        verify(tokenRepository, never()).save(any())
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString())
    }

    @Test
    fun `requestReset should issue and send token for existing user`() {
        val user = User.create("a@x.com", "User", "abc123", RoleId.generate())
        `when`(userRepository.findByEmail("a@x.com")).thenReturn(Optional.of(user))
        doNothing().`when`(tokenRepository).invalidateUnusedTokensForUser(any())
        doNothing().`when`(tokenRepository).save(any())
        doNothing().`when`(emailService).sendPasswordResetEmail(anyString(), anyString())

        service.requestReset("a@x.com")

        verify(tokenRepository).invalidateUnusedTokensForUser(user.id)
        verify(tokenRepository).save(
            argThat { token ->
                token.userId == user.id && !token.isExpired()
            },
        )
        verify(emailService).sendPasswordResetEmail(eq("a@x.com"), anyString())
    }

    @Test
    fun `confirmReset should throw if token not found`() {
        `when`(tokenRepository.findValidByToken(anyString())).thenReturn(Optional.empty())
        assertThrows(IllegalArgumentException::class.java) {
            service.confirmReset("badtoken", "newPW123X")
        }
    }

    @Test
    fun `confirmReset happy path changes password, marks token used, deletes sessions`() {
        val userId = UserId.generate()
        val token = PasswordResetToken.create(userId, "toktoktok---toktoktoktoktoktoktoktoktoktok", 60)
        `when`(tokenRepository.findValidByToken("toktok")).thenReturn(Optional.of(token))
        val user = User.create("b@z.com", "User2", "oldhash", RoleId.generate())
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))
        val hashed = "newhashed"
        `when`(passwordEncoder.encode("newPW123X")).thenReturn(hashed)
        doNothing().`when`(tokenRepository).markAsUsed(any())
        doNothing().`when`(userSessionRepository).deleteByUserId(any())
        `when`(userRepository.save(any())).thenReturn(user)

        service.confirmReset("toktok", "newPW123X")

        verify(passwordEncoder).encode("newPW123X")
        assertEquals(hashed, user.hashedPassword)
        verify(tokenRepository).markAsUsed(token.id)
        verify(userSessionRepository).deleteByUserId(userId)
        verify(userRepository).save(user)
    }
}
