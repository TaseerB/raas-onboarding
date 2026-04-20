package com.raas.aaa.service;

import com.raas.aaa.dto.AccountingRequest;
import com.raas.aaa.dto.AuthenticateRequest;
import com.raas.aaa.model.AccountingSessionEntity;
import com.raas.aaa.model.EventType;
import com.raas.aaa.repository.AccountingSessionRepository;
import com.raas.client.model.RadiusClientEntity;
import com.raas.client.repository.RadiusClientRepository;
import com.raas.common.exception.ApiException;
import com.raas.user.model.AuthorizationAttributeEntity;
import com.raas.user.model.UserEntity;
import com.raas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AaaServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RadiusClientRepository radiusClientRepository;
    @Mock private AccountingSessionRepository accountingSessionRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AaaService aaaService;

    private UserEntity enabledUser;
    private RadiusClientEntity enabledNas;

    @BeforeEach
    void setUp() {
        enabledUser = UserEntity.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .passwordHash("$2a$hashed")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Add an authorization attribute to verify it comes back in Accept
        enabledUser.getAuthorizationAttributes().add(
                AuthorizationAttributeEntity.builder()
                        .id(UUID.randomUUID())
                        .attributeName("Session-Timeout")
                        .attributeValue("3600")
                        .user(enabledUser)
                        .build()
        );

        enabledNas = RadiusClientEntity.builder()
                .id(UUID.randomUUID())
                .shortname("docker-gw")
                .ipAddress("192.168.65.1")
                .secretHash("$2a$hashed_secret")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── authenticate ─────────────────────────────────────────────────────────

    @Test
    void authenticate_validCredentials_returnsAccessAccept() {
        when(radiusClientRepository.findByIpAddress("192.168.65.1")).thenReturn(Optional.of(enabledNas));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(enabledUser));
        when(passwordEncoder.matches("secret", "$2a$hashed")).thenReturn(true);

        var response = aaaService.authenticate(new AuthenticateRequest("alice", "secret", "192.168.65.1"));

        assertThat(response.result()).isEqualTo("Access-Accept");
        assertThat(response.rejectReason()).isNull();
        assertThat(response.authorizationAttributes()).hasSize(1);
        assertThat(response.authorizationAttributes().get(0).attributeName()).isEqualTo("Session-Timeout");
    }

    @Test
    void authenticate_nasNotFound_returnsAccessReject() {
        when(radiusClientRepository.findByIpAddress(anyString())).thenReturn(Optional.empty());

        var response = aaaService.authenticate(new AuthenticateRequest("alice", "secret", "1.2.3.4"));

        assertThat(response.result()).isEqualTo("Access-Reject");
        assertThat(response.rejectReason()).isEqualTo("NAS client not authorized");
        verifyNoInteractions(userRepository);
    }

    @Test
    void authenticate_nasDisabled_returnsAccessReject() {
        enabledNas.setEnabled(false);
        when(radiusClientRepository.findByIpAddress(anyString())).thenReturn(Optional.of(enabledNas));

        var response = aaaService.authenticate(new AuthenticateRequest("alice", "secret", "192.168.65.1"));

        assertThat(response.result()).isEqualTo("Access-Reject");
        assertThat(response.rejectReason()).contains("NAS client not authorized");
    }

    @Test
    void authenticate_unknownUser_returnsAccessReject() {
        when(radiusClientRepository.findByIpAddress(anyString())).thenReturn(Optional.of(enabledNas));
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        var response = aaaService.authenticate(new AuthenticateRequest("nobody", "secret", "192.168.65.1"));

        assertThat(response.result()).isEqualTo("Access-Reject");
        assertThat(response.rejectReason()).isEqualTo("Unknown user");
    }

    @Test
    void authenticate_disabledUser_returnsAccessReject() {
        enabledUser.setEnabled(false);
        when(radiusClientRepository.findByIpAddress(anyString())).thenReturn(Optional.of(enabledNas));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(enabledUser));

        var response = aaaService.authenticate(new AuthenticateRequest("alice", "secret", "192.168.65.1"));

        assertThat(response.result()).isEqualTo("Access-Reject");
        assertThat(response.rejectReason()).isEqualTo("Account disabled");
    }

    @Test
    void authenticate_invalidPassword_returnsAccessReject() {
        when(radiusClientRepository.findByIpAddress(anyString())).thenReturn(Optional.of(enabledNas));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(enabledUser));
        when(passwordEncoder.matches("wrongpass", "$2a$hashed")).thenReturn(false);

        var response = aaaService.authenticate(new AuthenticateRequest("alice", "wrongpass", "192.168.65.1"));

        assertThat(response.result()).isEqualTo("Access-Reject");
        assertThat(response.rejectReason()).isEqualTo("Invalid credentials");
    }

    // ── recordAccounting ──────────────────────────────────────────────────────

    @Test
    void recordAccounting_success_returnsResponse() {
        var request = new AccountingRequest("alice", "192.168.65.1", "sess-001", EventType.START, null, null, null);
        var saved = AccountingSessionEntity.builder()
                .id(UUID.randomUUID())
                .username("alice")
                .nasIp("192.168.65.1")
                .sessionId("sess-001")
                .eventType(EventType.START)
                .occurredAt(Instant.now())
                .build();

        when(accountingSessionRepository.existsBySessionId("sess-001")).thenReturn(false);
        when(accountingSessionRepository.save(any())).thenReturn(saved);

        var response = aaaService.recordAccounting(request);

        assertThat(response.sessionId()).isEqualTo("sess-001");
        assertThat(response.eventType()).isEqualTo(EventType.START);
    }

    @Test
    void recordAccounting_duplicateSessionId_throwsConflict() {
        when(accountingSessionRepository.existsBySessionId("sess-001")).thenReturn(true);

        assertThatThrownBy(() -> aaaService.recordAccounting(
                new AccountingRequest("alice", null, "sess-001", EventType.STOP, 300, 1024L, 512L)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
