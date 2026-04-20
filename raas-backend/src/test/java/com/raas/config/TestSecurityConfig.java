package com.raas.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Overrides the OAuth2 JwtDecoder for all @SpringBootTest integration tests.
 * Accepts any bearer token value without making network calls to an authorization server.
 * Tests use SecurityMockMvcRequestPostProcessors.jwt() which bypasses this decoder entirely,
 * but this bean prevents Spring Security from failing at context startup when
 * no real issuer-uri is reachable.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("test-subject")
                .claim("scope", "openid")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
