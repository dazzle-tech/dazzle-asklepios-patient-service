package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPackageUsage;
import com.dazzle.asklepios.domain.enumeration.PatientPackageUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PatientPackageUsageRepository extends JpaRepository<PatientPackageUsage, Long> {

    List<PatientPackageUsage> findByPatientPackageIdOrderByIdDesc(Long patientPackageId);

    boolean existsByPatientPackageIdAndStatus(Long patientPackageId, PatientPackageUsageStatus status);

    long countByPatientPackageItemIdAndSourceIdAndStatus(Long patientPackageItemId, Long sourceId, PatientPackageUsageStatus status);

    /** Units of one item already counted on a package line (for the repeat limit). */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(u.quantity), 0) from PatientPackageUsage u "
                    + "where u.patientPackageItemId = :itemId and u.sourceId = :sourceId and u.status = :status")
    long sumQuantity(
            @org.springframework.data.repository.query.Param("itemId") Long patientPackageItemId,
            @org.springframework.data.repository.query.Param("sourceId") Long sourceId,
            @org.springframework.data.repository.query.Param("status") PatientPackageUsageStatus status);

    List<PatientPackageUsage> findByOrderSourceInAndOrderIdInAndStatus(
            Collection<String> orderSources, Collection<Long> orderIds, PatientPackageUsageStatus status);

    List<PatientPackageUsage> findByOrderSourceInAndOrderIdAndStatus(
            Collection<String> orderSources, Long orderId, PatientPackageUsageStatus status);
}
