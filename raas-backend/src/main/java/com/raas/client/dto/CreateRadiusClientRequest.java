package com.raas.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRadiusClientRequest(
        @NotBlank @Size(max = 64)            String shortname,
        @NotBlank @Size(max = 45)            String ipAddress,
        @NotBlank @Size(min = 8, max = 128)  String sharedSecret
) {}
