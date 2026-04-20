package com.raas.aaa.controller;

import com.raas.aaa.dto.AccountingRequest;
import com.raas.aaa.dto.AccountingSessionResponse;
import com.raas.aaa.dto.AuthenticateRequest;
import com.raas.aaa.dto.AuthenticateResponse;
import com.raas.aaa.service.AaaService;
import com.raas.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "AAA", description = "RADIUS Authentication, Authorization, and Accounting simulation")
public class AaaController {

    private final AaaService aaaService;

    /**
     * Simulates a RADIUS Access-Request/Accept/Reject exchange.
     * Both Access-Accept and Access-Reject return HTTP 200 — the {@code result} field
     * carries the RADIUS decision, consistent with real RADIUS protocol behaviour.
     */
    @PostMapping("/api/v1/aaa/authenticate")
    @Operation(summary = "Simulate RADIUS authentication (Access-Request → Accept/Reject)")
    public ApiResponse<AuthenticateResponse> authenticate(@Valid @RequestBody AuthenticateRequest request) {
        return ApiResponse.ok(aaaService.authenticate(request));
    }

    @PostMapping("/api/v1/accounting/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a RADIUS accounting event (START / STOP / INTERIM)")
    public ApiResponse<AccountingSessionResponse> recordSession(@Valid @RequestBody AccountingRequest request) {
        return ApiResponse.created(aaaService.recordAccounting(request));
    }

    @GetMapping("/api/v1/accounting/sessions")
    @Operation(summary = "List accounting sessions, optionally filtered by username")
    public ApiResponse<List<AccountingSessionResponse>> listSessions(
            @RequestParam(required = false) String username) {
        return ApiResponse.ok(aaaService.listSessions(username));
    }
}
