package com.worklog.domain.project;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MemberProjectAssignmentId value object.
 *
 * Tests the strongly-typed identifier for member-project assignments.
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("MemberProjectAssignmentId value object")
class MemberProjectAssignmentIdTest {

    @Nested
    @DisplayName("Generation")
    class Generation {

        @Test
        @DisplayName("should generate unique ID")
        void shouldGenerateUniqueId() {
            MemberProjectAssignmentId id1 = MemberProjectAssignmentId.generate();
            MemberProjectAssignmentId id2 = MemberProjectAssignmentId.generate();

            assertNotNull(id1);
            assertNotNull(id2);
            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should have non-null value after generation")
        void shouldHaveNonNullValue() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            assertNotNull(id.value());
        }
    }

    @Nested
    @DisplayName("Creation from UUID")
    class CreationFromUuid {

        @Test
        @DisplayName("should create from UUID")
        void shouldCreateFromUuid() {
            UUID uuid = UUID.randomUUID();

            MemberProjectAssignmentId id = MemberProjectAssignmentId.of(uuid);

            assertNotNull(id);
            assertEquals(uuid, id.value());
        }

        @Test
        @DisplayName("should throw exception for null UUID")
        void shouldRejectNullUuid() {
            IllegalArgumentException exception =
                    assertThrows(IllegalArgumentException.class, () -> MemberProjectAssignmentId.of((UUID) null));

            assertEquals("MemberProjectAssignmentId value cannot be null", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Creation from String")
    class CreationFromString {

        @Test
        @DisplayName("should create from valid UUID string")
        void shouldCreateFromString() {
            UUID uuid = UUID.randomUUID();
            String uuidString = uuid.toString();

            MemberProjectAssignmentId id = MemberProjectAssignmentId.of(uuidString);

            assertNotNull(id);
            assertEquals(uuid, id.value());
        }

        @Test
        @DisplayName("should throw exception for invalid UUID string")
        void shouldRejectInvalidString() {
            assertThrows(IllegalArgumentException.class, () -> MemberProjectAssignmentId.of("not-a-valid-uuid"));
        }

        @Test
        @DisplayName("should throw exception for null string")
        void shouldRejectNullString() {
            assertThrows(NullPointerException.class, () -> MemberProjectAssignmentId.of((String) null));
        }
    }

    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {

        @Test
        @DisplayName("should be equal when same UUID value")
        void shouldBeEqualWithSameValue() {
            UUID uuid = UUID.randomUUID();

            MemberProjectAssignmentId id1 = MemberProjectAssignmentId.of(uuid);
            MemberProjectAssignmentId id2 = MemberProjectAssignmentId.of(uuid);

            assertEquals(id1, id2);
            assertEquals(id1.hashCode(), id2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when different UUID value")
        void shouldNotBeEqualWithDifferentValue() {
            MemberProjectAssignmentId id1 = MemberProjectAssignmentId.generate();
            MemberProjectAssignmentId id2 = MemberProjectAssignmentId.generate();

            assertNotEquals(id1, id2);
        }

        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            assertEquals(id, id);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();

            assertNotEquals(null, id);
        }
    }

    @Nested
    @DisplayName("String representation")
    class StringRepresentation {

        @Test
        @DisplayName("should return UUID string from toString")
        void shouldReturnUuidString() {
            UUID uuid = UUID.randomUUID();
            MemberProjectAssignmentId id = MemberProjectAssignmentId.of(uuid);

            assertEquals(uuid.toString(), id.toString());
        }
    }
}
