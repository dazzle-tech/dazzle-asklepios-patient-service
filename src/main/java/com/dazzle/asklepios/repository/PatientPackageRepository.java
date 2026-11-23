package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPackage;
import com.dazzle.asklepios.domain.enumeration.PatientPackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PatientPackageRepository extends JpaRepository<PatientPackage, Long> {

    List<PatientPackage> findByPatientIdOrderByIdDesc(Long patientId);

    List<PatientPackage> findByPatientIdAndStatusInOrderByIdDesc(Long patientId, Collection<PatientPackageStatus> statuses);

    List<PatientPackage> findByEncounterIdOrderByIdAsc(Long encounterId);

    boolean existsByEncounterIdAndPackageSetupIdAndStatusNot(
            Long encounterId, Long packageSetupId, PatientPackageStatus status);
}
