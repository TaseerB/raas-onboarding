package com.raas.client.dto;

import java.time.Instant;
import java.util.UUID;

public record RadiusClientResponse(
        UUID id,
        String shortname,
        String ipAddress,
        boolean enabled,
        /** Always true — the plaintext secret is never returned. */
        boolean secretConfigured,
        Instant createdAt,
        Instant updatedAt
) {}
