package com.civitas.platform.modules.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {
    List<FraudAlert> findAllByOrderByCreatedAtDesc();
    boolean existsByTypeAndIpHashAndCreatedAtAfter(String type, String ipHash, Instant after);
}
