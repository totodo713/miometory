package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.tenant.TenantId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("JdbcMemberRepository")
class JdbcMemberRepositoryTest extends IntegrationTestBase {

    @Autowired
    private JdbcMemberRepository memberRepository;

    @Test
    @DisplayName("save and findById should handle null organization_id")
    void save_nullOrganizationId_roundTrips() {
        // Arrange — create member with null org via raw SQL
        UUID memberId = UUID.randomUUID();
        String email = "no-org-" + memberId + "@example.com";
        baseJdbcTemplate.update("""
                INSERT INTO members (id, tenant_id, organization_id, email, display_name, manager_id, is_active, version, created_at, updated_at)
                VALUES (?, '550e8400-e29b-41d4-a716-446655440001'::UUID, NULL, ?, ?, NULL, true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""", memberId, email, "No Org User " + memberId);

        // Act
        var found = memberRepository.findById(MemberId.of(memberId));

        // Assert
        assertTrue(found.isPresent());
        assertNull(found.get().getOrganizationId());
        assertEquals(email, found.get().getEmail());
        assertFalse(found.get().hasOrganization());
    }

    @Test
    @DisplayName("save should persist null organization_id via domain model")
    void save_memberWithNullOrg_persists() {
        // Arrange
        var member = Member.createForTenant(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
                "tenant-only-" + UUID.randomUUID() + "@example.com",
                "Tenant Only User");

        // Act
        memberRepository.save(member, 0);
        var found = memberRepository.findById(member.getId());

        // Assert
        assertTrue(found.isPresent());
        assertNull(found.get().getOrganizationId());
        assertFalse(found.get().hasOrganization());
        assertEquals(member.getEmail(), found.get().getEmail());
    }

    @Test
    @DisplayName("findDisplayNamesByIds should return empty map for empty input")
    void findDisplayNamesByIds_emptyInput_returnsEmptyMap() {
        Map<MemberId, String> result = memberRepository.findDisplayNamesByIds(Set.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findDisplayNamesByIds should return display names for existing members")
    void findDisplayNamesByIds_existingMembers_returnsNames() {
        // Arrange
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        createTestMember(id1, "member1-" + id1 + "@example.com");
        createTestMember(id2, "member2-" + id2 + "@example.com");

        // Act
        Map<MemberId, String> result =
                memberRepository.findDisplayNamesByIds(Set.of(MemberId.of(id1), MemberId.of(id2)));

        // Assert
        assertEquals(2, result.size());
        assertEquals("Test User " + id1, result.get(MemberId.of(id1)));
        assertEquals("Test User " + id2, result.get(MemberId.of(id2)));
    }

    @Test
    @DisplayName("findDisplayNamesByIds should omit non-existent member IDs")
    void findDisplayNamesByIds_nonExistentId_omitsFromResult() {
        // Arrange
        UUID existingId = UUID.randomUUID();
        UUID nonExistentId = UUID.randomUUID();
        createTestMember(existingId, "existing-" + existingId + "@example.com");

        // Act
        Map<MemberId, String> result =
                memberRepository.findDisplayNamesByIds(Set.of(MemberId.of(existingId), MemberId.of(nonExistentId)));

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test User " + existingId, result.get(MemberId.of(existingId)));
        assertNull(result.get(MemberId.of(nonExistentId)));
    }

    @Test
    @DisplayName("findDisplayNamesByIds should handle single ID")
    void findDisplayNamesByIds_singleId_returnsName() {
        // Arrange
        UUID id = UUID.randomUUID();
        createTestMember(id, "single-" + id + "@example.com");

        // Act
        Map<MemberId, String> result = memberRepository.findDisplayNamesByIds(Set.of(MemberId.of(id)));

        // Assert
        assertEquals(1, result.size());
        assertEquals("Test User " + id, result.get(MemberId.of(id)));
    }
}
