package com.worklog.domain.member;

import static org.junit.jupiter.api.Assertions.*;

import com.worklog.domain.organization.OrganizationId;
import com.worklog.domain.tenant.TenantId;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantAffiliationStatus")
class TenantAffiliationStatusTest {

    @Test
    @DisplayName("returns UNAFFILIATED when no members exist")
    void unaffiliated() {
        var result = TenantAffiliationStatus.fromMembers(Collections.emptyList());
        assertEquals(TenantAffiliationStatus.UNAFFILIATED, result);
    }

    @Test
    @DisplayName("returns AFFILIATED_NO_ORG when all members have no organization")
    void affiliatedNoOrg() {
        var member = Member.createForTenant(TenantId.generate(), "test@example.com", "Test User");
        var result = TenantAffiliationStatus.fromMembers(List.of(member));
        assertEquals(TenantAffiliationStatus.AFFILIATED_NO_ORG, result);
    }

    @Test
    @DisplayName("returns FULLY_ASSIGNED when at least one member has organization")
    void fullyAssigned() {
        var memberWithOrg =
                Member.create(TenantId.generate(), OrganizationId.generate(), "test@example.com", "Test User", null);
        var result = TenantAffiliationStatus.fromMembers(List.of(memberWithOrg));
        assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result);
    }

    @Test
    @DisplayName("returns FULLY_ASSIGNED when mixed: some with org, some without")
    void mixedAssignment() {
        var memberNoOrg = Member.createForTenant(TenantId.generate(), "a@example.com", "A");
        var memberWithOrg = Member.create(TenantId.generate(), OrganizationId.generate(), "b@example.com", "B", null);
        var result = TenantAffiliationStatus.fromMembers(List.of(memberNoOrg, memberWithOrg));
        assertEquals(TenantAffiliationStatus.FULLY_ASSIGNED, result);
    }
}
