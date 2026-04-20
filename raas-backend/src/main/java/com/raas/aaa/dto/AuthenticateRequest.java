package com.raas.aaa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticateRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank                 String password,
        @NotBlank @Size(max = 45) String nasIpAddress
) {}
