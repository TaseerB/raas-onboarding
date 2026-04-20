package com.raas.user.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        boolean enabled,
        List<AuthorizationAttributeDto> authorizationAttributes,
        Instant createdAt,
        Instant updatedAt
) {}
