package com.raas.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthorizationAttributeDto(
        @NotBlank @Size(max = 64)  String attributeName,
        @NotBlank @Size(max = 255) String attributeValue
) {}
