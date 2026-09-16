package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.FLACCPainScale;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FLACCPainScaleRepository extends JpaRepository<FLACCPainScale, Long> {

    List<FLACCPainScale> findByPatientIdOrderByCreatedDateDesc(Long patientId);

    List<FLACCPainScale> findByEncounterIdOrderByCreatedDateDesc(Long encounterId);

    List<FLACCPainScale> findByEncounterIdAndStatusNotOrderByCreatedDateDesc(
            Long encounterId,
            FLACCPainScaleStatus status
    );

    Optional<FLACCPainScale> findFirstByEncounterIdAndStatusOrderByCreatedDateDesc(
            Long encounterId,
            FLACCPainScaleStatus status
    );
}

