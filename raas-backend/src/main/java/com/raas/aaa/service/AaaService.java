package com.raas.aaa.service;

import com.raas.aaa.dto.AccountingRequest;
import com.raas.aaa.dto.AccountingSessionResponse;
import com.raas.aaa.dto.AuthenticateRequest;
import com.raas.aaa.dto.AuthenticateResponse;
import com.raas.aaa.model.AccountingSessionEntity;
import com.raas.aaa.repository.AccountingSessionRepository;
import com.raas.client.repository.RadiusClientRepository;
import com.raas.common.exception.ApiException;
import com.raas.user.dto.AuthorizationAttributeDto;
import com.raas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AaaService {

    private final UserRepository userRepository;
    private final RadiusClientRepository radiusClientRepository;
    private final AccountingSessionRepository accountingSessionRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Simulates a RADIUS Access-Request evaluation:
     * 1. Validate NAS client (IP must be registered and enabled)
     * 2. Look up user by username
     * 3. Check user is enabled
     * 4. Verify password (BCrypt)
     * 5. Return Access-Accept with authorization attributes, or Access-Reject
     */
    @Transactional(readOnly = true)
    public AuthenticateResponse authenticate(AuthenticateRequest request) {
        // Step 1 — NAS client check
        var nasClient = radiusClientRepository.findByIpAddress(request.nasIpAddress()).orElse(null);
        if (nasClient == null || !nasClient.isEnabled()) {
            log.warn("Auth rejected — NAS not authorized: {}", request.nasIpAddress());
            return AuthenticateResponse.reject(request.username(), "NAS client not authorized");
        }

        // Step 2 — User lookup (with attributes loaded eagerly via @EntityGraph)
        var user = userRepository.findByUsername(request.username()).orElse(null);
        if (user == null) {
            log.warn("Auth rejected — unknown user: {}", request.username());
            return AuthenticateResponse.reject(request.username(), "Unknown user");
        }

        // Step 3 — Enabled check
        if (!user.isEnabled()) {
            log.warn("Auth rejected — disabled user: {}", request.username());
            return AuthenticateResponse.reject(request.username(), "Account disabled");
        }

        // Step 4 — Password verification
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Auth rejected — invalid credentials for user: {}", request.username());
            return AuthenticateResponse.reject(request.username(), "Invalid credentials");
        }

        // Step 5 — Access-Accept
        var attrs = user.getAuthorizationAttributes().stream()
                .map(a -> new AuthorizationAttributeDto(a.getAttributeName(), a.getAttributeValue()))
                .toList();

        log.info("Auth accepted — user: {}, nas: {}", request.username(), request.nasIpAddress());
        return AuthenticateResponse.accept(request.username(), attrs);
    }

    /**
     * Records a RADIUS Accounting-Request event.
     * Session IDs are unique; duplicate submissions return 409.
     */
    @Transactional
    public AccountingSessionResponse recordAccounting(AccountingRequest request) {
        if (accountingSessionRepository.existsBySessionId(request.sessionId())) {
            throw ApiException.conflict("Accounting session already recorded: " + request.sessionId());
        }

        var entity = AccountingSessionEntity.builder()
                .username(request.username())
                .nasIp(request.nasIp())
                .sessionId(request.sessionId())
                .eventType(request.eventType())
                .sessionTime(request.sessionTime())
                .bytesIn(request.bytesIn())
                .bytesOut(request.bytesOut())
                .build();

        var saved = accountingSessionRepository.save(entity);
        log.info("Recorded accounting session: {} for user: {}", saved.getSessionId(), saved.getUsername());
        return toSessionResponse(saved);
    }

    /**
     * Lists accounting sessions, optionally filtered by username, ordered by time descending.
     */
    @Transactional(readOnly = true)
    public List<AccountingSessionResponse> listSessions(String username) {
        if (username != null && !username.isBlank()) {
            return accountingSessionRepository
                    .findByUsernameOrderByOccurredAtDesc(username)
                    .stream().map(this::toSessionResponse).toList();
        }
        return accountingSessionRepository
                .findAll(Sort.by(Sort.Direction.DESC, "occurredAt"))
                .stream().map(this::toSessionResponse).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AccountingSessionResponse toSessionResponse(AccountingSessionEntity e) {
        return new AccountingSessionResponse(
                e.getId(),
                e.getUsername(),
                e.getNasIp(),
                e.getSessionId(),
                e.getEventType(),
                e.getSessionTime(),
                e.getBytesIn(),
                e.getBytesOut(),
                e.getOccurredAt()
        );
    }
}
