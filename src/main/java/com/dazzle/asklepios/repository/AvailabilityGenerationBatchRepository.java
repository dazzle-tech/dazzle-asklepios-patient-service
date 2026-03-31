package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.AvailabilityGenerationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvailabilityGenerationBatchRepository extends JpaRepository<AvailabilityGenerationBatch, Long> {
}
