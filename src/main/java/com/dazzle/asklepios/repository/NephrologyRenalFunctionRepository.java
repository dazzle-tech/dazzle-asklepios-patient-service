package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.NephrologyRenalFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NephrologyRenalFunctionRepository
        extends JpaRepository<NephrologyRenalFunction, Long> {

    Optional<NephrologyRenalFunction>
    findFirstByPatientIdAndEncounterIdAndIsActiveTrue(
            Long patientId,
            Long encounterId
    );
}
