package com.raas.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raas.config.TestSecurityConfig;
import com.raas.user.dto.AuthorizationAttributeDto;
import com.raas.user.dto.CreateUserRequest;
import com.raas.user.dto.UpdateUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@Transactional
class UserControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createUser_validRequest_returns201WithBody() throws Exception {
        var request = new CreateUserRequest("integrationuser", "securePass1!", null);

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("integrationuser"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void createUser_withAuthorizationAttributes_returns201WithAttributes() throws Exception {
        var attrs = List.of(new AuthorizationAttributeDto("Session-Timeout", "3600"));
        var request = new CreateUserRequest("attruser", "securePass1!", attrs);

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.authorizationAttributes[0].attributeName").value("Session-Timeout"))
                .andExpect(jsonPath("$.data.authorizationAttributes[0].attributeValue").value("3600"));
    }

    @Test
    void createUser_duplicateUsername_returns409() throws Exception {
        var request = new CreateUserRequest("dupuser", "securePass1!", null);

        // First create succeeds
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second create with same username conflicts
        mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createUser_passwordTooShort_returns400() throws Exception {
        var request = new CreateUserRequest("shortpwduser", "short", null);

        mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createUser_withoutJwt_returns401() throws Exception {
        var request = new CreateUserRequest("unauthuser", "securePass1!", null);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listUsers_returns200WithEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void getUser_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void updateUser_disableUser_returns200() throws Exception {
        // First create
        var create = new CreateUserRequest("updateme", "securePass1!", null);
        var createResult = mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Disable
        var update = new UpdateUserRequest(null, false, null);
        mockMvc.perform(put("/api/v1/users/{id}", id)
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    void deleteUser_returns204() throws Exception {
        // Create first
        var create = new CreateUserRequest("deleteme", "securePass1!", null);
        var createResult = mockMvc.perform(post("/api/v1/users")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isCreated())
                .andReturn();

        var id = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/id").asText();

        // Delete
        mockMvc.perform(delete("/api/v1/users/{id}", id).with(jwt()))
                .andExpect(status().isNoContent());

        // Verify gone
        mockMvc.perform(get("/api/v1/users/{id}", id).with(jwt()))
                .andExpect(status().isNotFound());
    }
}
