package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiscountCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountCodeUsageRepository extends JpaRepository<DiscountCodeUsage, Long> {

    Optional<DiscountCodeUsage> findByPatientServiceProductIdAndDiscountCodeId(
            Long patientServiceProductId,
            Long discountCodeId
    );

    List<DiscountCodeUsage> findByEncounterIdOrderByCreatedDateDescIdDesc(Long encounterId);
}
