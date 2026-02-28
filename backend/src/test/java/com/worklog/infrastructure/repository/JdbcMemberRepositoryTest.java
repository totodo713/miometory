package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.tenant.TenantId;
import java.util.List;
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

    @Test
    @DisplayName("findExistingEmailsInTenant should return empty set for empty input")
    void findExistingEmailsInTenant_emptyInput_returnsEmptySet() {
        Set<String> result = memberRepository.findExistingEmailsInTenant(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findExistingEmailsInTenant should return matching emails")
    void findExistingEmailsInTenant_existingMembers_returnsEmails() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        String email1 = "member1-" + id1 + "@example.com";
        String email2 = "member2-" + id2 + "@example.com";
        createTestMember(id1, email1);
        createTestMember(id2, email2);

        Set<String> result = memberRepository.findExistingEmailsInTenant(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
                List.of(email1, "nonexistent@example.com"));

        assertEquals(1, result.size());
        assertTrue(result.contains(email1.toLowerCase()));
    }

    @Test
    @DisplayName("findExistingEmailsInTenant should normalize query parameter to lowercase")
    void findExistingEmailsInTenant_caseInsensitive_returnsMatch() {
        UUID id = UUID.randomUUID();
        String email = "casetest-" + id + "@example.com";
        createTestMember(id, email);

        Set<String> result = memberRepository.findExistingEmailsInTenant(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), List.of(email.toUpperCase()));

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("findExistingEmailsInTenant should not return members from other tenants")
    void findExistingEmailsInTenant_otherTenant_returnsEmpty() {
        UUID id = UUID.randomUUID();
        String email = "tenant-test-" + id + "@example.com";
        createTestMember(id, email);

        Set<String> result =
                memberRepository.findExistingEmailsInTenant(TenantId.of(UUID.randomUUID()), List.of(email));

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findByEmail should return member when found in tenant")
    void findByEmail_existingMember_returnsMember() {
        UUID memberId = UUID.randomUUID();
        String email = "find-email-" + memberId + "@example.com";
        createTestMember(memberId, email);

        var found = memberRepository.findByEmail(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), email);

        assertTrue(found.isPresent());
        assertEquals(email, found.get().getEmail());
        assertEquals(memberId, found.get().getId().value());
    }

    @Test
    @DisplayName("findByEmail should return empty for non-existent email")
    void findByEmail_nonExistent_returnsEmpty() {
        var found = memberRepository.findByEmail(
                TenantId.of(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")), "does-not-exist@example.com");

        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("findByOrganization should return active members")
    void findByOrganization_returnsActiveMembers() {
        UUID memberId = UUID.randomUUID();
        createTestMember(memberId, "org-member-" + memberId + "@example.com");

        List<Member> members = memberRepository.findByOrganization(
                OrganizationId.of(UUID.fromString("880e8400-e29b-41d4-a716-446655440001")));

        assertTrue(members.stream().anyMatch(m -> m.getId().value().equals(memberId)));
    }

    @Test
    @DisplayName("isDirectSubordinateOf should return true for direct report")
    void isDirectSubordinateOf_directReport_returnsTrue() {
        UUID managerId = UUID.randomUUID();
        UUID subordinateId = UUID.randomUUID();
        createTestMember(managerId, "mgr-" + managerId + "@example.com");
        createTestMember(subordinateId, "sub-" + subordinateId + "@example.com");
        setManagerForMember(subordinateId, managerId);

        assertTrue(memberRepository.isDirectSubordinateOf(MemberId.of(managerId), MemberId.of(subordinateId)));
    }

    @Test
    @DisplayName("isDirectSubordinateOf should return false for non-subordinate")
    void isDirectSubordinateOf_notSubordinate_returnsFalse() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        createTestMember(id1, "peer1-" + id1 + "@example.com");
        createTestMember(id2, "peer2-" + id2 + "@example.com");

        assertFalse(memberRepository.isDirectSubordinateOf(MemberId.of(id1), MemberId.of(id2)));
    }

    @Test
    @DisplayName("findAllByEmail should return member when found")
    void findAllByEmail_existingMember_returnsList() {
        UUID memberId = UUID.randomUUID();
        String email = "all-email-" + memberId + "@example.com";
        createTestMember(memberId, email);

        List<Member> results = memberRepository.findAllByEmail(email);

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(m -> m.getId().value().equals(memberId)));
        assertEquals(
                email,
                results.stream()
                        .filter(m -> m.getId().value().equals(memberId))
                        .findFirst()
                        .orElseThrow()
                        .getEmail());
    }

    @Test
    @DisplayName("findAllByEmail should normalize query parameter to lowercase")
    void findAllByEmail_caseInsensitive_returnsList() {
        UUID memberId = UUID.randomUUID();
        String email = "case-test-" + memberId + "@example.com";
        createTestMember(memberId, email);

        List<Member> results = memberRepository.findAllByEmail(email.toUpperCase());

        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(m -> m.getId().value().equals(memberId)));
    }

    @Test
    @DisplayName("findAllByEmail should return empty list for unknown email")
    void findAllByEmail_unknownEmail_returnsEmptyList() {
        List<Member> results = memberRepository.findAllByEmail("nonexistent-" + UUID.randomUUID() + "@example.com");

        assertTrue(results.isEmpty());
    }
}
