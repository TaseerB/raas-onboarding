package com.raas.user.controller;

import com.raas.common.dto.ApiResponse;
import com.raas.user.dto.CreateUserRequest;
import com.raas.user.dto.UpdateUserRequest;
import com.raas.user.dto.UserResponse;
import com.raas.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "RADIUS user management — credentials and authorization attributes")
public class UserController {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a RADIUS user")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.created(userService.createUser(request));
    }

    @GetMapping
    @Operation(summary = "List all RADIUS users")
    public ApiResponse<List<UserResponse>> listUsers() {
        return ApiResponse.ok(userService.listUsers());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a RADIUS user by ID")
    public ApiResponse<UserResponse> getUser(@PathVariable UUID id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a RADIUS user (password, enabled flag, or authorization attributes)")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a RADIUS user (cascades to authorization attributes)")
    public void deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
    }
}
