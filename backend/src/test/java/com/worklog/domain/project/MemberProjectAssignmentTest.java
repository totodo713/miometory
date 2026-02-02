package com.worklog.domain.project;

import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MemberProjectAssignment entity.
 * 
 * Tests business logic for member-to-project assignment creation
 * and state management (activation/deactivation).
 * 
 * These are pure unit tests with no dependencies on Spring or database.
 */
@DisplayName("MemberProjectAssignment entity")
class MemberProjectAssignmentTest {
    
    private TenantId testTenantId;
    private MemberId testMemberId;
    private ProjectId testProjectId;
    private MemberId testAssignedBy;
    
    @BeforeEach
    void setUp() {
        testTenantId = TenantId.of(UUID.randomUUID());
        testMemberId = MemberId.of(UUID.randomUUID());
        testProjectId = ProjectId.of(UUID.randomUUID());
        testAssignedBy = MemberId.of(UUID.randomUUID());
    }
    
    @Nested
    @DisplayName("Creation via factory method")
    class CreationViaFactoryMethod {
        
        @Test
        @DisplayName("should create new assignment with valid data")
        void shouldCreateValidAssignment() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId,
                testMemberId,
                testProjectId,
                testAssignedBy
            );
            
            assertNotNull(assignment);
            assertNotNull(assignment.getId());
            assertEquals(testTenantId, assignment.getTenantId());
            assertEquals(testMemberId, assignment.getMemberId());
            assertEquals(testProjectId, assignment.getProjectId());
            assertEquals(testAssignedBy, assignment.getAssignedBy());
            assertNotNull(assignment.getAssignedAt());
            assertTrue(assignment.isActive(), "New assignments should be active by default");
        }
        
        @Test
        @DisplayName("should create assignment with unique ID")
        void shouldCreateWithUniqueId() {
            MemberProjectAssignment assignment1 = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            MemberProjectAssignment assignment2 = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            assertNotEquals(assignment1.getId(), assignment2.getId());
        }
        
        @Test
        @DisplayName("should create assignment with null assignedBy")
        void shouldCreateWithNullAssignedBy() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId,
                testMemberId,
                testProjectId,
                null  // assignedBy can be null
            );
            
            assertNotNull(assignment);
            assertNull(assignment.getAssignedBy());
        }
        
        @Test
        @DisplayName("should set assignedAt to current time")
        void shouldSetAssignedAtToCurrentTime() {
            Instant before = Instant.now();
            
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            Instant after = Instant.now();
            
            assertNotNull(assignment.getAssignedAt());
            assertTrue(
                !assignment.getAssignedAt().isBefore(before) && 
                !assignment.getAssignedAt().isAfter(after),
                "AssignedAt should be between before and after creation time"
            );
        }
    }
    
    @Nested
    @DisplayName("Creation via constructor (reconstitution)")
    class CreationViaConstructor {
        
        @Test
        @DisplayName("should reconstitute assignment with all fields")
        void shouldReconstituteAssignment() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();
            Instant assignedAt = Instant.now().minusSeconds(3600);
            
            MemberProjectAssignment assignment = new MemberProjectAssignment(
                id,
                testTenantId,
                testMemberId,
                testProjectId,
                assignedAt,
                testAssignedBy,
                false  // Inactive
            );
            
            assertEquals(id, assignment.getId());
            assertEquals(testTenantId, assignment.getTenantId());
            assertEquals(testMemberId, assignment.getMemberId());
            assertEquals(testProjectId, assignment.getProjectId());
            assertEquals(assignedAt, assignment.getAssignedAt());
            assertEquals(testAssignedBy, assignment.getAssignedBy());
            assertFalse(assignment.isActive());
        }
        
        @Test
        @DisplayName("should throw exception for null ID")
        void shouldRejectNullId() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MemberProjectAssignment(
                    null,
                    testTenantId,
                    testMemberId,
                    testProjectId,
                    Instant.now(),
                    testAssignedBy,
                    true
                )
            );
            
            assertEquals("Assignment ID cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for null tenant ID")
        void shouldRejectNullTenantId() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(),
                    null,
                    testMemberId,
                    testProjectId,
                    Instant.now(),
                    testAssignedBy,
                    true
                )
            );
            
            assertEquals("Tenant ID cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for null member ID")
        void shouldRejectNullMemberId() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(),
                    testTenantId,
                    null,
                    testProjectId,
                    Instant.now(),
                    testAssignedBy,
                    true
                )
            );
            
            assertEquals("Member ID cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for null project ID")
        void shouldRejectNullProjectId() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(),
                    testTenantId,
                    testMemberId,
                    null,
                    Instant.now(),
                    testAssignedBy,
                    true
                )
            );
            
            assertEquals("Project ID cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should throw exception for null assignedAt")
        void shouldRejectNullAssignedAt() {
            NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> new MemberProjectAssignment(
                    MemberProjectAssignmentId.generate(),
                    testTenantId,
                    testMemberId,
                    testProjectId,
                    null,
                    testAssignedBy,
                    true
                )
            );
            
            assertEquals("Assigned timestamp cannot be null", exception.getMessage());
        }
        
        @Test
        @DisplayName("should allow null assignedBy in constructor")
        void shouldAllowNullAssignedByInConstructor() {
            MemberProjectAssignment assignment = new MemberProjectAssignment(
                MemberProjectAssignmentId.generate(),
                testTenantId,
                testMemberId,
                testProjectId,
                Instant.now(),
                null,  // assignedBy can be null
                true
            );
            
            assertNotNull(assignment);
            assertNull(assignment.getAssignedBy());
        }
    }
    
    @Nested
    @DisplayName("Activation and Deactivation")
    class ActivationAndDeactivation {
        
        private MemberProjectAssignment assignment;
        
        @BeforeEach
        void setUp() {
            assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
        }
        
        @Test
        @DisplayName("should deactivate an active assignment")
        void shouldDeactivateActiveAssignment() {
            assertTrue(assignment.isActive(), "Precondition: assignment should be active");
            
            assignment.deactivate();
            
            assertFalse(assignment.isActive());
        }
        
        @Test
        @DisplayName("should activate an inactive assignment")
        void shouldActivateInactiveAssignment() {
            assignment.deactivate();
            assertFalse(assignment.isActive(), "Precondition: assignment should be inactive");
            
            assignment.activate();
            
            assertTrue(assignment.isActive());
        }
        
        @Test
        @DisplayName("should allow multiple deactivations")
        void shouldAllowMultipleDeactivations() {
            assignment.deactivate();
            assignment.deactivate();
            
            assertFalse(assignment.isActive());
        }
        
        @Test
        @DisplayName("should allow multiple activations")
        void shouldAllowMultipleActivations() {
            assignment.activate();
            assignment.activate();
            
            assertTrue(assignment.isActive());
        }
        
        @Test
        @DisplayName("should allow toggling activation state")
        void shouldAllowTogglingActivationState() {
            assignment.deactivate();
            assertFalse(assignment.isActive());
            
            assignment.activate();
            assertTrue(assignment.isActive());
            
            assignment.deactivate();
            assertFalse(assignment.isActive());
        }
    }
    
    @Nested
    @DisplayName("Equality and HashCode")
    class EqualityAndHashCode {
        
        @Test
        @DisplayName("should be equal when same ID")
        void shouldBeEqualWithSameId() {
            MemberProjectAssignmentId id = MemberProjectAssignmentId.generate();
            
            MemberProjectAssignment assignment1 = new MemberProjectAssignment(
                id, testTenantId, testMemberId, testProjectId,
                Instant.now(), testAssignedBy, true
            );
            MemberProjectAssignment assignment2 = new MemberProjectAssignment(
                id, testTenantId, testMemberId, testProjectId,
                Instant.now(), testAssignedBy, false  // Different active state
            );
            
            assertEquals(assignment1, assignment2);
            assertEquals(assignment1.hashCode(), assignment2.hashCode());
        }
        
        @Test
        @DisplayName("should not be equal when different ID")
        void shouldNotBeEqualWithDifferentId() {
            MemberProjectAssignment assignment1 = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            MemberProjectAssignment assignment2 = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            assertNotEquals(assignment1, assignment2);
        }
        
        @Test
        @DisplayName("should be equal to itself")
        void shouldBeEqualToItself() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            assertEquals(assignment, assignment);
        }
        
        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            assertNotEquals(null, assignment);
        }
        
        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            assertNotEquals("not an assignment", assignment);
        }
    }
    
    @Nested
    @DisplayName("String representation")
    class StringRepresentation {
        
        @Test
        @DisplayName("should include key fields in toString")
        void shouldIncludeKeyFieldsInToString() {
            MemberProjectAssignment assignment = MemberProjectAssignment.create(
                testTenantId, testMemberId, testProjectId, testAssignedBy
            );
            
            String string = assignment.toString();
            
            assertTrue(string.contains("MemberProjectAssignment"));
            assertTrue(string.contains("memberId"));
            assertTrue(string.contains("projectId"));
            assertTrue(string.contains("isActive"));
        }
    }
}
