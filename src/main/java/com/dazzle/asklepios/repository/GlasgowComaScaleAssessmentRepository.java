package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.GlasgowComaScaleAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlasgowComaScaleAssessmentRepository extends JpaRepository<GlasgowComaScaleAssessment, Long> {

    Page<GlasgowComaScaleAssessment> findAllByEncounter_Id(
            Long encounterId,
            Pageable pageable
    );

    Page<GlasgowComaScaleAssessment> findAllByEncounter_IdAndCancelledAtIsNull(
            Long encounterId,
            Pageable pageable
    );

}