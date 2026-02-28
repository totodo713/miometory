package com.worklog.application.service;

import com.worklog.domain.member.Member;
import com.worklog.domain.shared.DomainException;
import com.worklog.domain.tenant.TenantId;
import com.worklog.domain.user.UserId;
import com.worklog.infrastructure.persistence.JdbcUserRepository;
import com.worklog.infrastructure.repository.JdbcMemberRepository;
import java.util.List;
import java.util.UUID;
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
    public List<UserSearchResult> searchUsersForAssignment(String emailPartial, UUID tenantId) {
        TenantId tid = TenantId.of(tenantId);
        var users = userRepository.searchByEmailPartial(emailPartial);

        return users.stream()
                .map(user -> {
                    boolean alreadyInTenant =
                            memberRepository.findByEmail(tid, user.getEmail()).isPresent();
                    return new UserSearchResult(
                            user.getId().value().toString(), user.getEmail(), user.getName(), alreadyInTenant);
                })
                .toList();
    }

    public void assignUserToTenant(UUID userId, UUID tenantId, String displayName) {
        var user = userRepository
                .findById(UserId.of(userId))
                .orElseThrow(() -> new DomainException("USER_NOT_FOUND", "User not found: " + userId));

        TenantId tid = TenantId.of(tenantId);

        if (memberRepository.findByEmail(tid, user.getEmail()).isPresent()) {
            throw new DomainException("DUPLICATE_TENANT_ASSIGNMENT", "User is already assigned to tenant: " + tenantId);
        }

        Member member = Member.createForTenant(tid, user.getEmail(), displayName);
        memberRepository.save(member);
    }

    public record UserSearchResult(String userId, String email, String name, boolean isAlreadyInTenant) {}
}
