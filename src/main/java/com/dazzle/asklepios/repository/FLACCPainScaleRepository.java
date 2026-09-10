package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FLACCPainScale;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FLACCPainScaleRepository extends JpaRepository<FLACCPainScale, Long> {

    List<FLACCPainScale> findByPatientId(Long patientId);

    List<FLACCPainScale> findByEncounterId(Long encounterId);

    List<FLACCPainScale> findByEncounterIdAndStatusNot(
            Long encounterId,
            FLACCPainScaleStatus status
    );
}

