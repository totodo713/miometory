package com.worklog.api;

import com.worklog.api.dto.AssignedProject;
import com.worklog.api.dto.AssignedProjectsResponse;
import com.worklog.api.dto.MemberResponse;
import com.worklog.api.dto.SubordinatesResponse;
import com.worklog.domain.member.Member;
import com.worklog.domain.member.MemberId;
import com.worklog.domain.shared.DomainException;
import com.worklog.infrastructure.projection.AssignedProjectInfo;
import com.worklog.infrastructure.repository.JdbcMemberProjectAssignmentRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for member-related operations.
 *
 * Provides endpoints for member lookup and manager-subordinate relationships
 * required for proxy entry feature (US7).
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final JdbcMemberRepository memberRepository;
    private final JdbcMemberProjectAssignmentRepository memberProjectAssignmentRepository;

    public MemberController(
            JdbcMemberRepository memberRepository,
            JdbcMemberProjectAssignmentRepository memberProjectAssignmentRepository) {
        this.memberRepository = memberRepository;
        this.memberProjectAssignmentRepository = memberProjectAssignmentRepository;
    }

    /**
     * Get a member by ID.
     *
     * GET /api/v1/members/{id}
     *
     * @param id Member UUID
     * @return 200 OK with member details
     * @throws DomainException if member not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> getMember(@PathVariable UUID id) {
        Member member = memberRepository
                .findById(MemberId.of(id))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + id));

        return ResponseEntity.ok(toResponse(member));
    }

    /**
     * Get subordinates of a manager.
     *
     * GET /api/v1/members/{id}/subordinates?recursive=false
     *
     * By default, returns only direct subordinates (recursive=false).
     * Set recursive=true to include all subordinates in the management hierarchy.
     *
     * @param id Manager's member UUID
     * @param recursive Whether to include indirect subordinates (default: false)
     * @return 200 OK with list of subordinates
     */
    @GetMapping("/{id}/subordinates")
    public ResponseEntity<SubordinatesResponse> getSubordinates(
            @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean recursive) {
        // Verify the manager exists
        memberRepository
                .findById(MemberId.of(id))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + id));

        List<Member> subordinates;
        if (recursive) {
            subordinates = memberRepository.findAllSubordinates(MemberId.of(id));
        } else {
            subordinates = memberRepository.findDirectSubordinates(MemberId.of(id));
        }

        List<MemberResponse> responses =
                subordinates.stream().map(this::toResponse).toList();

        return ResponseEntity.ok(new SubordinatesResponse(responses, responses.size(), recursive));
    }

    /**
     * Get assigned projects for a member.
     *
     * GET /api/v1/members/{id}/projects
     *
     * Returns projects that the member is assigned to and can log time against.
     * Only returns active assignments for active projects that are valid on today's date.
     *
     * @param id Member UUID
     * @return 200 OK with list of assigned projects
     */
    @GetMapping("/{id}/projects")
    public ResponseEntity<AssignedProjectsResponse> getAssignedProjects(@PathVariable UUID id) {
        // Verify the member exists
        memberRepository
                .findById(MemberId.of(id))
                .orElseThrow(() -> new DomainException("MEMBER_NOT_FOUND", "Member not found: " + id));

        List<AssignedProjectInfo> projectInfos =
                memberProjectAssignmentRepository.findActiveProjectsForMember(MemberId.of(id), LocalDate.now());

        List<AssignedProject> projects = projectInfos.stream()
                .map(info -> new AssignedProject(info.projectId().toString(), info.code(), info.name()))
                .toList();

        return ResponseEntity.ok(new AssignedProjectsResponse(projects, projects.size()));
    }

    /**
     * Check if a manager can enter time on behalf of a member.
     *
     * GET /api/v1/members/{managerId}/can-proxy/{memberId}
     *
     * @param managerId Manager's member UUID
     * @param memberId Target member's UUID
     * @return 200 OK with { canProxy: true/false }
     */
    @GetMapping("/{managerId}/can-proxy/{memberId}")
    public ResponseEntity<CanProxyResponse> canProxy(@PathVariable UUID managerId, @PathVariable UUID memberId) {
        // Self-entry is always allowed
        if (managerId.equals(memberId)) {
            return ResponseEntity.ok(new CanProxyResponse(true, "Self-entry is always allowed"));
        }

        boolean canProxy = memberRepository.isSubordinateOf(MemberId.of(managerId), MemberId.of(memberId));

        String reason = canProxy
                ? "Manager can enter time for this subordinate"
                : "Member is not a subordinate of this manager";

        return ResponseEntity.ok(new CanProxyResponse(canProxy, reason));
    }

    /**
     * Convert Member entity to MemberResponse DTO.
     */
    private MemberResponse toResponse(Member member) {
        return new MemberResponse(
                member.getId().value(),
                member.getEmail(),
                member.getDisplayName(),
                member.getManagerId() != null ? member.getManagerId().value() : null,
                member.isActive());
    }

    /**
     * Response DTO for can-proxy check.
     */
    public record CanProxyResponse(boolean canProxy, String reason) {}
}
