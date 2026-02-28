package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TenantAssignmentService {

    private final JdbcUserRepository userRepository;
    private final JdbcMemberRepository memberRepository;

    public TenantAssignmentService(JdbcUserRepository userRepository, JdbcMemberRepository memberRepository) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public UserSearchResponse searchUsersForAssignment(String emailPartial, UUID tenantId) {
        TenantId tid = TenantId.of(tenantId);
        var users = userRepository.searchByEmailPartial(emailPartial);

        Set<String> emails = users.stream().map(u -> u.getEmail()).collect(Collectors.toSet());
        Set<String> existingEmails = memberRepository.findEmailsExistingInTenant(tid, emails);

        List<UserSearchResult> results = users.stream()
                .map(user -> new UserSearchResult(
                        user.getId().value().toString(),
                        user.getEmail(),
                        user.getName(),
                        existingEmails.contains(user.getEmail().toLowerCase())))
                .toList();
        return new UserSearchResponse(results);
    }

    public void assignUserToTenant(UUID userId, UUID tenantId, String displayName) {
        var user = userRepository
                .findById(UserId.of(userId))
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found"));

        TenantId tid = TenantId.of(tenantId);

        if (memberRepository.findByEmail(tid, user.getEmail()).isPresent()) {
            throw new DomainException("DUPLICATE_TENANT_ASSIGNMENT", "User is already assigned to this tenant");
        }

        Member member = Member.createForTenant(tid, user.getEmail(), displayName);
        try {
            memberRepository.save(member);
        } catch (DataIntegrityViolationException ex) {
            throw new DomainException("DUPLICATE_TENANT_ASSIGNMENT", "User is already assigned to this tenant");
        }
    }

    public record UserSearchResponse(List<UserSearchResult> users) {}

    public record UserSearchResult(String userId, String email, String name, boolean isAlreadyInTenant) {}
}
