package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.BillingEligibilitySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingEligibilitySnapshotRepository
        extends JpaRepository<BillingEligibilitySnapshot, Long> {

    Optional<BillingEligibilitySnapshot> findByEncounterId(Long encounterId);

    boolean existsByEncounterId(Long encounterId);
}
