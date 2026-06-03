package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.WaseelEligibilityRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WaseelEligibilityRequestRepository
        extends JpaRepository<WaseelEligibilityRequest, Long> {

    Optional<WaseelEligibilityRequest>
    findFirstByPatientIdAndRequestStatusAndEligibilityResponseIdIsNotNullOrderByCreatedDateDesc(
            Long patientId,
            String requestStatus
    );
}