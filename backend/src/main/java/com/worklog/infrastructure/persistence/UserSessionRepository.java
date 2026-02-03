package com.worklog.infrastructure.persistence;

import com.worklog.domain.session.UserSession;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for UserSession entity.
 */
@Repository
public interface UserSessionRepository extends CrudRepository<UserSession, UUID> {
}
