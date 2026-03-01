/**
 * TypeScript type definitions for tenant affiliation and membership
 *
 * Mirrors the backend TenantAffiliationStatus enum and UserStatusResponse DTO.
 */

export type TenantAffiliationState = "UNAFFILIATED" | "AFFILIATED_NO_ORG" | "FULLY_ASSIGNED";

export interface TenantMembership {
  memberId: string;
  tenantId: string;
  tenantName: string;
  organizationId: string | null;
  organizationName: string | null;
}

export interface UserStatusResponse {
  userId: string;
  email: string;
  state: TenantAffiliationState;
  memberships: TenantMembership[];
}
