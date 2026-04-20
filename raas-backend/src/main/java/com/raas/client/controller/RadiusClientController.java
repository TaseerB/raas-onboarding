package com.raas.client.controller;

import com.raas.client.dto.CreateRadiusClientRequest;
import com.raas.client.dto.RadiusClientResponse;
import com.raas.client.dto.UpdateRadiusClientRequest;
import com.raas.client.service.RadiusClientService;
import com.raas.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "NAS Clients", description = "RADIUS NAS client (Network Access Server) management")
public class RadiusClientController {

    private final RadiusClientService radiusClientService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a NAS client")
    public ApiResponse<RadiusClientResponse> createClient(@Valid @RequestBody CreateRadiusClientRequest request) {
        return ApiResponse.created(radiusClientService.createClient(request));
    }

    @GetMapping
    @Operation(summary = "List all registered NAS clients")
    public ApiResponse<List<RadiusClientResponse>> listClients() {
        return ApiResponse.ok(radiusClientService.listClients());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a NAS client by ID")
    public ApiResponse<RadiusClientResponse> getClient(@PathVariable UUID id) {
        return ApiResponse.ok(radiusClientService.getClient(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a NAS client (IP, shared secret, or enabled flag)")
    public ApiResponse<RadiusClientResponse> updateClient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRadiusClientRequest request) {
        return ApiResponse.ok(radiusClientService.updateClient(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deregister a NAS client")
    public void deleteClient(@PathVariable UUID id) {
        radiusClientService.deleteClient(id);
    }
}
