package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ReferralRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ReferralRequestRepository extends JpaRepository<ReferralRequest, Long> {

    Page<ReferralRequest> findByPatient_Id(Long patientId, Pageable pageable);

    Page<ReferralRequest> findByEncounter_Id(Long encounterId, Pageable pageable);

    Page<ReferralRequest> findByToFacilityIdAndCreatedDateBetween(
            Long toFacilityId,
            Instant from,
            Instant to,
            Pageable pageable
    );
}