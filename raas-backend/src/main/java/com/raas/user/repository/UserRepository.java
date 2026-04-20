package com.raas.user.repository;

import com.raas.user.model.UserEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    @EntityGraph(attributePaths = "authorizationAttributes")
    @Override
    Optional<UserEntity> findById(UUID id);

    @EntityGraph(attributePaths = "authorizationAttributes")
    Optional<UserEntity> findByUsername(String username);

    @EntityGraph(attributePaths = "authorizationAttributes")
    @Override
    List<UserEntity> findAll();

    boolean existsByUsername(String username);
}
