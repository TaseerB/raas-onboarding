package com.raas.aaa.repository;

import com.raas.aaa.model.AccountingSessionEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountingSessionRepository extends JpaRepository<AccountingSessionEntity, UUID> {

    boolean existsBySessionId(String sessionId);

    List<AccountingSessionEntity> findByUsernameOrderByOccurredAtDesc(String username);
}
