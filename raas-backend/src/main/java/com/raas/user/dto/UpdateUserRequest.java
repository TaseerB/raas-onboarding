package com.raas.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateUserRequest(
        /** If null, existing password is kept unchanged. */
        @Size(min = 8, max = 128) String password,

        /** If null, enabled flag is kept unchanged. */
        Boolean enabled,

        /**
         * If null, existing authorization attributes are kept unchanged.
         * If an empty list, all existing attributes are removed.
         */
        List<@Valid AuthorizationAttributeDto> authorizationAttributes
) {}
