package com.worklog.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.worklog.application.service.SystemSettingsService;
import com.worklog.application.service.SystemSettingsService.SystemDefaultPatterns;
import com.worklog.domain.user.User;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemSettingsController (Unit)")
class SystemSettingsControllerUnitTest {

    @Mock
    private SystemSettingsService systemSettingsService;

    @Mock
    private JdbcUserRepository userRepository;

    @Mock
    private Authentication authentication;

    private SystemSettingsController controller;

    @BeforeEach
    void setUp() {
        controller = new SystemSettingsController(systemSettingsService, userRepository);
    }

    @Nested
    @DisplayName("GET /api/v1/admin/system/settings/patterns")
    class GetPatterns {

        @Test
        @DisplayName("should return system default patterns")
        void shouldReturnPatterns() {
            SystemDefaultPatterns patterns = new SystemDefaultPatterns(4, 1, 1);
            when(systemSettingsService.getDefaultPatterns()).thenReturn(patterns);

            ResponseEntity<SystemDefaultPatterns> response = controller.getPatterns();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(4, response.getBody().fiscalYearStartMonth());
            assertEquals(1, response.getBody().fiscalYearStartDay());
            assertEquals(1, response.getBody().monthlyPeriodStartDay());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/admin/system/settings/patterns")
    class UpdatePatterns {

        @Test
        @DisplayName("should update patterns and return no content")
        void shouldUpdatePatterns() {
            UUID userId = UUID.randomUUID();
            User mockUser = mock(User.class);
            when(mockUser.getId()).thenReturn(UserId.of(userId));
            when(authentication.getName()).thenReturn("admin@test.com");
            when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(mockUser));

            SystemSettingsController.UpdatePatternsRequest request =
                    new SystemSettingsController.UpdatePatternsRequest(10, 1, 15);

            ResponseEntity<Void> response = controller.updatePatterns(request, authentication);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(systemSettingsService).updateDefaultPatterns(10, 1, 15, userId);
        }

        @Test
        @DisplayName("should pass null userId when user not found")
        void shouldHandleMissingUser() {
            when(authentication.getName()).thenReturn("unknown@test.com");
            when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            SystemSettingsController.UpdatePatternsRequest request =
                    new SystemSettingsController.UpdatePatternsRequest(4, 1, 1);

            ResponseEntity<Void> response = controller.updatePatterns(request, authentication);

            assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
            verify(systemSettingsService).updateDefaultPatterns(4, 1, 1, null);
        }
    }
}
