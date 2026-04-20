package com.raas.client.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "radius_clients")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RadiusClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 64)
    private String shortname;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    /**
     * BCrypt hash of the RADIUS shared secret.
     * The plaintext secret is never stored or returned via any API.
     */
    @Column(name = "secret_hash", nullable = false)
    private String secretHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
