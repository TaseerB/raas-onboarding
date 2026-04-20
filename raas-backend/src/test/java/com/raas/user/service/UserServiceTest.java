package com.raas.user.service;

import com.raas.common.exception.ApiException;
import com.raas.user.dto.AuthorizationAttributeDto;
import com.raas.user.dto.CreateUserRequest;
import com.raas.user.dto.UpdateUserRequest;
import com.raas.user.model.AuthorizationAttributeEntity;
import com.raas.user.model.UserEntity;
import com.raas.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UUID userId;
    private UserEntity existingUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        existingUser = UserEntity.builder()
                .id(userId)
                .username("alice")
                .passwordHash("$2a$hashed")
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createUser_success_returnsUserResponse() {
        var request = new CreateUserRequest("alice", "password123", null);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.createUser(request);

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.enabled()).isTrue();
        assertThat(response.authorizationAttributes()).isEmpty();
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void createUser_withAuthorizationAttributes_savesAttributes() {
        var attrs = List.of(new AuthorizationAttributeDto("Session-Timeout", "3600"));
        var request = new CreateUserRequest("bob", "password123", attrs);
        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.createUser(request);

        assertThat(response.authorizationAttributes()).hasSize(1);
        assertThat(response.authorizationAttributes().get(0).attributeName()).isEqualTo("Session-Timeout");
    }

    @Test
    void createUser_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(new CreateUserRequest("alice", "pass1234", null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void getUser_found_returnsResponse() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        var response = userService.getUser(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void getUser_notFound_throwsNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateUser_passwordChange_hashesNewPassword() {
        var request = new UpdateUserRequest("newpassword1", null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode("newpassword1")).thenReturn("$2a$newhash");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.updateUser(userId, request);

        assertThat(existingUser.getPasswordHash()).isEqualTo("$2a$newhash");
        verify(passwordEncoder).encode("newpassword1");
    }

    @Test
    void updateUser_disableUser_setsEnabledFalse() {
        var request = new UpdateUserRequest(null, false, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.updateUser(userId, request);

        assertThat(response.enabled()).isFalse();
    }

    @Test
    void updateUser_replaceAttributes_clearsAndAddsNew() {
        // Seed an existing attribute on the user
        var existingAttr = AuthorizationAttributeEntity.builder()
                .id(UUID.randomUUID())
                .attributeName("Old-Attr")
                .attributeValue("old-value")
                .user(existingUser)
                .build();
        existingUser.getAuthorizationAttributes().add(existingAttr);

        var newAttrs = List.of(new AuthorizationAttributeDto("Session-Timeout", "7200"));
        var request = new UpdateUserRequest(null, null, newAttrs);
        when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = userService.updateUser(userId, request);

        assertThat(existingUser.getAuthorizationAttributes()).hasSize(1);
        assertThat(existingUser.getAuthorizationAttributes().get(0).getAttributeName())
                .isEqualTo("Session-Timeout");
        assertThat(response.authorizationAttributes()).hasSize(1);
    }

    @Test
    void deleteUser_success_callsRepository() {
        when(userRepository.existsById(userId)).thenReturn(true);

        userService.deleteUser(userId);

        verify(userRepository).deleteById(userId);
    }

    @Test
    void deleteUser_notFound_throwsNotFound() {
        when(userRepository.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser(UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
