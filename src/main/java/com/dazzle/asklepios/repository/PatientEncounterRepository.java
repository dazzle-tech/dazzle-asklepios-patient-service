package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PatientEncounterRepository extends JpaRepository<PatientEncounter, Long> , JpaSpecificationExecutor<PatientEncounter> {


    Page<PatientEncounter> findByPatientIdAndDepartmentIdAndStatusInOrderByCreatedDateDesc(
            Long patientId,
            Long departmentId,
            List<EncounterStatus> statuses,
            Pageable pageable
    );

    long countByFacilityIdAndEncounterDate(Long facilityId, LocalDate encounterDate);

    long countDistinctPatient_IdByDepartmentIdAndEncounterDate(
            Long departmentId,
            LocalDate encounterDate
    );
    long countByDepartmentIdAndEncounterDateAndStatusIn(
            Long departmentId,
            LocalDate encounterDate,
            List<EncounterStatus> statuses
    );

    long countByDepartmentIdAndEncounterDateAndStatus(
            Long departmentId,
            LocalDate encounterDate,
            EncounterStatus status
    );

    boolean existsByPatient_IdAndStatusAndIdNot(
            Long patientId,
            EncounterStatus status,
            Long id
    );
}
