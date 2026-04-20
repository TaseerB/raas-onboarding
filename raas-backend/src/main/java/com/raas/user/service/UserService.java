package com.raas.user.service;

import com.raas.common.exception.ApiException;
import com.raas.user.dto.AuthorizationAttributeDto;
import com.raas.user.dto.CreateUserRequest;
import com.raas.user.dto.UpdateUserRequest;
import com.raas.user.dto.UserResponse;
import com.raas.user.model.AuthorizationAttributeEntity;
import com.raas.user.model.UserEntity;
import com.raas.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Username already exists: " + request.username());
        }

        var user = UserEntity.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        if (request.authorizationAttributes() != null) {
            request.authorizationAttributes().forEach(dto -> user.getAuthorizationAttributes()
                    .add(buildAttribute(dto, user)));
        }

        var saved = userRepository.save(user);
        log.info("Created RADIUS user: {}", saved.getUsername());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return toResponse(findById(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        var user = findById(id);

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.authorizationAttributes() != null) {
            user.getAuthorizationAttributes().clear();
            request.authorizationAttributes().forEach(dto -> user.getAuthorizationAttributes()
                    .add(buildAttribute(dto, user)));
        }

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            throw ApiException.notFound("User not found: " + id);
        }
        userRepository.deleteById(id);
        log.info("Deleted RADIUS user: {}", id);
    }

    /**
     * Used by AaaService for authentication. Loads the user with attributes eagerly.
     */
    @Transactional(readOnly = true)
    public UserEntity findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("User not found: " + username));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserEntity findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
    }

    private AuthorizationAttributeEntity buildAttribute(AuthorizationAttributeDto dto, UserEntity user) {
        return AuthorizationAttributeEntity.builder()
                .attributeName(dto.attributeName())
                .attributeValue(dto.attributeValue())
                .user(user)
                .build();
    }

    UserResponse toResponse(UserEntity user) {
        var attrs = user.getAuthorizationAttributes().stream()
                .map(a -> new AuthorizationAttributeDto(a.getAttributeName(), a.getAttributeValue()))
                .toList();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.isEnabled(),
                attrs,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
