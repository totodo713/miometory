package com.worklog.domain.member;

/**
 * Represents a user's tenant affiliation status.
 *
 * Determined by checking the user's member records:
 * - UNAFFILIATED: no member records exist
 * - AFFILIATED_NO_ORG: member record(s) exist but all have organization_id=null
 * - FULLY_ASSIGNED: at least one member record has organization_id set
 */
public enum TenantAffiliationStatus {
    UNAFFILIATED,
    AFFILIATED_NO_ORG,
    FULLY_ASSIGNED;

    /**
     * Determines status from a list of members.
     *
     * @param members list of Member entities for the user (can be empty)
     * @return the affiliation status
     */
    public static TenantAffiliationStatus fromMembers(java.util.List<Member> members) {
        if (members.isEmpty()) {
            return UNAFFILIATED;
        }
        boolean anyHasOrg = members.stream().anyMatch(Member::hasOrganization);
        return anyHasOrg ? FULLY_ASSIGNED : AFFILIATED_NO_ORG;
    }
}
