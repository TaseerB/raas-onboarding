package com.raas.aaa.dto;

import com.raas.aaa.model.EventType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AccountingRequest(
        @NotBlank @Size(max = 64)  String username,
        @Size(max = 45)            String nasIp,
        @NotBlank @Size(max = 128) String sessionId,
        @NotNull                   EventType eventType,
        @Min(0)                    Integer sessionTime,
        @Min(0)                    Long bytesIn,
        @Min(0)                    Long bytesOut
) {}
