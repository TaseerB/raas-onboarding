package com.raas.client.service;

import com.raas.client.dto.CreateRadiusClientRequest;
import com.raas.client.dto.RadiusClientResponse;
import com.raas.client.dto.UpdateRadiusClientRequest;
import com.raas.client.model.RadiusClientEntity;
import com.raas.client.repository.RadiusClientRepository;
import com.raas.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RadiusClientService {

    private final RadiusClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RadiusClientResponse createClient(CreateRadiusClientRequest request) {
        if (clientRepository.existsByShortname(request.shortname())) {
            throw ApiException.conflict("Client shortname already exists: " + request.shortname());
        }

        var client = RadiusClientEntity.builder()
                .shortname(request.shortname())
                .ipAddress(request.ipAddress())
                .secretHash(passwordEncoder.encode(request.sharedSecret()))
                .build();

        var saved = clientRepository.save(client);
        log.info("Registered RADIUS client: {} ({})", saved.getShortname(), saved.getIpAddress());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RadiusClientResponse getClient(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<RadiusClientResponse> listClients() {
        return clientRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public RadiusClientResponse updateClient(UUID id, UpdateRadiusClientRequest request) {
        var client = findById(id);

        if (request.sharedSecret() != null && !request.sharedSecret().isBlank()) {
            client.setSecretHash(passwordEncoder.encode(request.sharedSecret()));
        }
        if (request.ipAddress() != null && !request.ipAddress().isBlank()) {
            client.setIpAddress(request.ipAddress());
        }
        if (request.enabled() != null) {
            client.setEnabled(request.enabled());
        }

        return toResponse(clientRepository.save(client));
    }

    @Transactional
    public void deleteClient(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw ApiException.notFound("RADIUS client not found: " + id);
        }
        clientRepository.deleteById(id);
        log.info("Deregistered RADIUS client: {}", id);
    }

    /**
     * Used by AaaService to validate the originating NAS.
     */
    @Transactional(readOnly = true)
    public Optional<RadiusClientEntity> findByIpAddress(String ipAddress) {
        return clientRepository.findByIpAddress(ipAddress);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RadiusClientEntity findById(UUID id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("RADIUS client not found: " + id));
    }

    private RadiusClientResponse toResponse(RadiusClientEntity entity) {
        return new RadiusClientResponse(
                entity.getId(),
                entity.getShortname(),
                entity.getIpAddress(),
                entity.isEnabled(),
                true,   // secretConfigured: plaintext is never stored or returned
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
