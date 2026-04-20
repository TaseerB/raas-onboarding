package com.raas.client.dto;

import jakarta.validation.constraints.Size;

public record UpdateRadiusClientRequest(
        /** If null, existing shared secret is kept unchanged. */
        @Size(min = 8, max = 128) String sharedSecret,

        /** If null, IP address is kept unchanged. */
        @Size(max = 45) String ipAddress,

        /** If null, enabled flag is kept unchanged. */
        Boolean enabled
) {}
