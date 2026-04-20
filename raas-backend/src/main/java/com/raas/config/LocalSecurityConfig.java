package com.raas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;

/**
 * Mock JWT decoder for the "local" profile.
 * Accepts any Bearer token value without contacting a real authorization server.
 * Usage: mvn spring-boot:run -Dspring-boot.run.profiles=local
 */
@Configuration
@Profile("local")
public class LocalSecurityConfig {

    @Bean
    @Primary
    public JwtDecoder localJwtDecoder() {
        return token -> Jwt.withTokenValue(token)
                .header("alg", "none")
                .subject("local-dev-user")
                .claim("scope", "openid")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
