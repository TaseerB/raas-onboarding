package com.raas.aaa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.raas.user.dto.AuthorizationAttributeDto;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthenticateResponse(
        /** "Access-Accept" or "Access-Reject" */
        String result,
        String username,
        /** Present only on Access-Reject. */
        String rejectReason,
        /** Present only on Access-Accept. */
        List<AuthorizationAttributeDto> authorizationAttributes
) {
    public static AuthenticateResponse accept(String username,
                                              List<AuthorizationAttributeDto> attrs) {
        return new AuthenticateResponse("Access-Accept", username, null, attrs);
    }

    public static AuthenticateResponse reject(String username, String reason) {
        return new AuthenticateResponse("Access-Reject", username, reason, null);
    }
}
