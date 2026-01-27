package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.EncounterVaccination;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EncounterVaccinationRepository extends JpaRepository<EncounterVaccination, Long> {

    Page<EncounterVaccination> findByPatient_Id(Long patientId, Pageable pageable);
    Page<EncounterVaccination> findByPatient_IdAndStatus(Long patientId, EncounterVaccinationStatus status, Pageable pageable);
    Page<EncounterVaccination> findByPatient_IdAndStatusNot(Long patientId, EncounterVaccinationStatus status, Pageable pageable);

    Page<EncounterVaccination> findByEncounterId(Long encounterId, Pageable pageable);
    Page<EncounterVaccination> findByEncounterIdAndStatus(Long encounterId, EncounterVaccinationStatus status, Pageable pageable);
    Page<EncounterVaccination> findByEncounterIdAndStatusNot(Long encounterId, EncounterVaccinationStatus status, Pageable pageable);

    Page<EncounterVaccination> findByPatient_IdAndEncounterId(Long patientId, Long encounterId, Pageable pageable);
    Page<EncounterVaccination> findByPatient_IdAndEncounterIdAndStatus(Long patientId, Long encounterId, EncounterVaccinationStatus status, Pageable pageable);
    Page<EncounterVaccination> findByPatient_IdAndEncounterIdAndStatusNot(Long patientId, Long encounterId, EncounterVaccinationStatus status, Pageable pageable);

    interface VaccineIdOnly {
        Long getVaccineId();
    }
    List<VaccineIdOnly> findDistinctByPatient_Id(Long patientId);

    Page<EncounterVaccination> findByPatient_IdAndVaccineId(Long patientId, Long vaccineId, Pageable pageable);


    Page<EncounterVaccination> findByPatient_IdAndVaccineIdAndStatusNot(
            Long patientId,
            Long vaccineId,
            EncounterVaccinationStatus status,
            Pageable pageable
    );

    interface VaccineBrandIdOnly {
        Long getVaccineBrandId();
    }

    List<VaccineBrandIdOnly> findDistinctBrandIdsByPatient_IdAndVaccineId(Long patientId, Long vaccineId);

    List<VaccineBrandIdOnly> findDistinctBrandIdsByPatient_IdAndVaccineIdAndStatusNot(
            Long patientId,
            Long vaccineId,
            EncounterVaccinationStatus status
    );


    interface VaccineDoseIdOnly {
        Long getVaccineDoseId();
    }

    List<VaccineDoseIdOnly> findDistinctDoseIdsByPatient_IdAndVaccineId(Long patientId, Long vaccineId);

    List<VaccineDoseIdOnly> findDistinctDoseIdsByPatient_IdAndVaccineIdAndStatusNot(
            Long patientId,
            Long vaccineId,
            EncounterVaccinationStatus status
    );
}
