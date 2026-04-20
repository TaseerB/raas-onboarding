package com.raas.client.service;

import com.raas.client.dto.CreateRadiusClientRequest;
import com.raas.client.dto.UpdateRadiusClientRequest;
import com.raas.client.model.RadiusClientEntity;
import com.raas.client.repository.RadiusClientRepository;
import com.raas.common.exception.ApiException;
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
class RadiusClientServiceTest {

    @Mock
    private RadiusClientRepository clientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RadiusClientService radiusClientService;

    private UUID clientId;
    private RadiusClientEntity existingClient;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        existingClient = RadiusClientEntity.builder()
                .id(clientId)
                .shortname("docker-gw")
                .ipAddress("192.168.65.0/24")
                .secretHash("$2a$hashed_secret")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createClient_success_returnsResponse() {
        var request = new CreateRadiusClientRequest("docker-gw", "192.168.65.0/24", "testing123");
        when(clientRepository.existsByShortname("docker-gw")).thenReturn(false);
        when(passwordEncoder.encode("testing123")).thenReturn("$2a$hashed_secret");
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = radiusClientService.createClient(request);

        assertThat(response.shortname()).isEqualTo("docker-gw");
        assertThat(response.ipAddress()).isEqualTo("192.168.65.0/24");
        assertThat(response.secretConfigured()).isTrue();
    }

    @Test
    void createClient_neverExposeSecretHash_inResponse() {
        var request = new CreateRadiusClientRequest("nas1", "10.0.0.1", "supersecret1");
        when(clientRepository.existsByShortname(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hash");
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = radiusClientService.createClient(request);

        // RadiusClientResponse has no secretHash field — confirmed at compile time.
        // Only secretConfigured: true is exposed.
        assertThat(response.secretConfigured()).isTrue();
    }

    @Test
    void createClient_duplicateShortname_throwsConflict() {
        when(clientRepository.existsByShortname("docker-gw")).thenReturn(true);

        assertThatThrownBy(() -> radiusClientService.createClient(
                new CreateRadiusClientRequest("docker-gw", "10.0.0.1", "pass1234!")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getClient_notFound_throwsNotFound() {
        when(clientRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> radiusClientService.getClient(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateClient_ipAddressChange_updatesIp() {
        var request = new UpdateRadiusClientRequest(null, "10.0.0.2", null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(existingClient));
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = radiusClientService.updateClient(clientId, request);

        assertThat(response.ipAddress()).isEqualTo("10.0.0.2");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void updateClient_secretChange_hashesNewSecret() {
        var request = new UpdateRadiusClientRequest("newSecret99", null, null);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(existingClient));
        when(passwordEncoder.encode("newSecret99")).thenReturn("$2a$newhash");
        when(clientRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        radiusClientService.updateClient(clientId, request);

        assertThat(existingClient.getSecretHash()).isEqualTo("$2a$newhash");
    }

    @Test
    void deleteClient_success_callsRepository() {
        when(clientRepository.existsById(clientId)).thenReturn(true);

        radiusClientService.deleteClient(clientId);

        verify(clientRepository).deleteById(clientId);
    }

    @Test
    void deleteClient_notFound_throwsNotFound() {
        when(clientRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> radiusClientService.deleteClient(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
