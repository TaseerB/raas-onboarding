package com.raas.aaa.dto;

import com.raas.aaa.model.EventType;

import java.time.Instant;
import java.util.UUID;

public record AccountingSessionResponse(
        UUID id,
        String username,
        String nasIp,
        String sessionId,
        EventType eventType,
        Integer sessionTime,
        Long bytesIn,
        Long bytesOut,
        Instant occurredAt
) {}
