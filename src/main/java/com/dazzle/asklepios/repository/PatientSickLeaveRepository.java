package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientSickLeave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface PatientSickLeaveRepository extends JpaRepository<PatientSickLeave, Long> {
    List<PatientSickLeave> findByPatientIdOrderByStartDateDesc(Long patientId);

    List<PatientSickLeave> findByEncounterIdOrderByStartDateDesc(Long encounterId);

    List<PatientSickLeave> findByPatientIdAndStartDateGreaterThanEqualOrderByStartDateDesc(
            Long patientId, LocalDate startDate);

    List<PatientSickLeave> findByPatientIdAndEndDateLessThanEqualOrderByStartDateDesc(
            Long patientId, LocalDate endDate);

    List<PatientSickLeave> findByPatientIdAndStartDateGreaterThanEqualAndEndDateLessThanEqualOrderByStartDateDesc(
            Long patientId, LocalDate startDate, LocalDate endDate);
}