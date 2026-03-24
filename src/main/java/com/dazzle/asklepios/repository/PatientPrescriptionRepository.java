package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientPrescriptionRepository extends JpaRepository<PatientPrescription, Long> {


    Page<PatientPrescription> findByPatientIdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndStatus(
            Long patientId, Long encounterId, PrescriptionStatus status, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndStatusNot(
            Long patientId, Long encounterId, PrescriptionStatus status, Pageable pageable);

    Optional<PatientPrescription> findTopByEncounterIdAndStatusOrderByCreatedDateDesc(
            Long encounterId,
            PrescriptionStatus status
    );
    Page<PatientPrescription> findByPatientId(Long patientId, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndStatus(
            Long patientId,
            PrescriptionStatus status,
            Pageable pageable
    );

    Page<PatientPrescription> findByPatientIdAndStatusNot(
            Long patientId,
            PrescriptionStatus status,
            Pageable pageable
    );
}
