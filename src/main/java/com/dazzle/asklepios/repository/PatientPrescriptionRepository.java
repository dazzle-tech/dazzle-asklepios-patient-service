package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.PatientPrescription;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionUrgencyLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PatientPrescriptionRepository extends JpaRepository<PatientPrescription, Long> {

    Page<PatientPrescription> findByPatientId(Long patientId, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndStatus(
            Long patientId, Long encounterId, PrescriptionStatus status, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndStatusNot(
            Long patientId, Long encounterId, PrescriptionStatus status, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndUrgencyLevel(
            Long patientId, Long encounterId, PrescriptionUrgencyLevel urgencyLevel, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndEncounterIdAndStatusAndUrgencyLevel(
            Long patientId, Long encounterId, PrescriptionStatus status, PrescriptionUrgencyLevel urgencyLevel, Pageable pageable);

    Page<PatientPrescription> findByPrescriptionNum(Long prescriptionNum, Pageable pageable);

    Page<PatientPrescription> findByPatientIdAndPrescriptionNum(Long patientId, Long prescriptionNum, Pageable pageable);

    Optional<PatientPrescription> findTopByEncounterIdAndStatusOrderByCreatedDateDesc(
            Long encounterId,
            PrescriptionStatus status
    );

}

