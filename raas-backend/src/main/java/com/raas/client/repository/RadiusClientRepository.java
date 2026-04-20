package com.raas.client.repository;

import com.raas.client.model.RadiusClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RadiusClientRepository extends JpaRepository<RadiusClientEntity, UUID> {

    boolean existsByShortname(String shortname);

    Optional<RadiusClientEntity> findByShortname(String shortname);

    Optional<RadiusClientEntity> findByIpAddress(String ipAddress);
}
