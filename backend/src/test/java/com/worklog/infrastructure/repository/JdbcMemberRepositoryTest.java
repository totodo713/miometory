package com.worklog.infrastructure.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.IntegrationTestBase;
import com.worklog.domain.member.MemberId;
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
    @DisplayName("findExistingEmailsInTenant should be case-insensitive")
    void findExistingEmailsInTenant_caseInsensitive_returnsMatch() {
        UUID id = UUID.randomUUID();
        String email = "CaseTest-" + id + "@example.com";
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
}
