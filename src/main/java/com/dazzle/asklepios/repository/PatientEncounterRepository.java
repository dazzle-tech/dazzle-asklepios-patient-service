package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientEncounterRepository extends JpaRepository<PatientEncounter, Long>, JpaSpecificationExecutor<PatientEncounter> {


    @EntityGraph(attributePaths = "appointment")
    Page<PatientEncounter> findByPatientIdAndDepartmentIdAndStatusInOrderByCreatedDateDesc(
            Long patientId,
            Long departmentId,
            List<TreatmentStatus> statuses,
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
            List<TreatmentStatus> statuses
    );

    long countByDepartmentIdAndEncounterDateAndStatus(
            Long departmentId,
            LocalDate encounterDate,
            TreatmentStatus status
    );

    @EntityGraph(attributePaths = "appointment")
    Page<PatientEncounter> findByPatientIdOrderByCreatedDateDesc(
            Long patientId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"appointment", "patient"})
    List<PatientEncounter> findAllByPatientIdOrderByCreatedDateDesc(
            Long patientId
    );

    @EntityGraph(attributePaths = "appointment")
    PatientEncounter findByAppointment_Id(Long appointmentId);

    @EntityGraph(attributePaths = "appointment")
    Optional<PatientEncounter> findFirstByPatientIdAndStatusAndEncounterDateLessThanEqualOrderByEncounterDateDesc(
            Long patientId,
            TreatmentStatus status,
            LocalDate encounterDate
    );

    long countByDepartmentIdAndEncounterDateBetween(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate
    );

    long countByDepartmentIdAndEncounterDateBetweenAndStatus(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate,
            TreatmentStatus status
    );

    long countByDepartmentIdAndEncounterDateBetweenAndStatusIn(
            Long departmentId,
            LocalDate fromDate,
            LocalDate toDate,
            List<TreatmentStatus> statuses
    );

}
