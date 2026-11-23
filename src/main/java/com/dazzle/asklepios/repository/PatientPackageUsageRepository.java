package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PatientPackageUsageRepository extends JpaRepository<PatientPackageUsage, Long> {

    boolean existsByPatientPackageIdAndStatus(Long patientPackageId, PatientPackageUsageStatus status);

    List<PatientPackageUsage> findByOrderSourceInAndOrderIdAndStatus(
            Collection<String> orderSources, Long orderId, PatientPackageUsageStatus status);
}
