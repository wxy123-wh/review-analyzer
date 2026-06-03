package com.wh.reputation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformRepository extends JpaRepository<PlatformEntity, Long> {
    Optional<PlatformEntity> findByName(String name);
}

