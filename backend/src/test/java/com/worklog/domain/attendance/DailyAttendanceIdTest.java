package com.worklog.domain.attendance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DailyAttendanceId value object")
class DailyAttendanceIdTest {

    @Nested
    @DisplayName("Generation")
    class Generation {
        @Test
        @DisplayName("should generate a unique ID")
        void shouldGenerateUniqueId() {
            DailyAttendanceId id1 = DailyAttendanceId.generate();
            DailyAttendanceId id2 = DailyAttendanceId.generate();
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should generate non-null value")
        void shouldGenerateNonNullValue() {
            DailyAttendanceId id = DailyAttendanceId.generate();
            assertNotNull(id.value());
        }
    }

    @Nested
    @DisplayName("Creation from UUID")
    class CreationFromUuid {
        @Test
        @DisplayName("should create from UUID value")
        void shouldCreateFromUuid() {
            UUID uuid = UUID.randomUUID();
            DailyAttendanceId id = DailyAttendanceId.of(uuid);
            assertEquals(uuid, id.value());
        }

        @Test
        @DisplayName("should reject null UUID")
        void shouldRejectNullUuid() {
            assertThrows(IllegalArgumentException.class, () -> DailyAttendanceId.of((UUID) null));
        }
    }

    @Nested
    @DisplayName("Creation from String")
    class CreationFromString {
        @Test
        @DisplayName("should create from valid string")
        void shouldCreateFromString() {
            UUID uuid = UUID.randomUUID();
            DailyAttendanceId id = DailyAttendanceId.of(uuid.toString());
            assertEquals(uuid, id.value());
        }

        @Test
        @DisplayName("should reject invalid string")
        void shouldRejectInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> DailyAttendanceId.of("not-a-uuid"));
        }
    }

    @Nested
    @DisplayName("String representation")
    class StringRepresentation {
        @Test
        @DisplayName("should return UUID string")
        void shouldReturnUuidString() {
            UUID uuid = UUID.randomUUID();
            DailyAttendanceId id = DailyAttendanceId.of(uuid);
            assertEquals(uuid.toString(), id.toString());
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {
        @Test
        @DisplayName("should be equal when same UUID")
        void shouldBeEqualWithSameUuid() {
            UUID uuid = UUID.randomUUID();
            DailyAttendanceId id1 = DailyAttendanceId.of(uuid);
            DailyAttendanceId id2 = DailyAttendanceId.of(uuid);
            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when different UUID")
        void shouldNotBeEqualWithDifferentUuid() {
            DailyAttendanceId id1 = DailyAttendanceId.generate();
            DailyAttendanceId id2 = DailyAttendanceId.generate();
            assertNotEquals(id1, id2);
        }
    }
}
