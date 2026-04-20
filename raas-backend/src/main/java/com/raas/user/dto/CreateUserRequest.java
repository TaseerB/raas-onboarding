package com.raas.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 64)   String username,
        @NotBlank @Size(min = 8, max = 128)  String password,
        List<@Valid AuthorizationAttributeDto> authorizationAttributes
) {}
