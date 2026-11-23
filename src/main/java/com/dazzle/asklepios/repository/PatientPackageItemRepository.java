package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientPackageItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PatientPackageItemRepository extends JpaRepository<PatientPackageItem, Long> {

    List<PatientPackageItem> findByPatientPackageIdOrderByIdAsc(Long patientPackageId);

    List<PatientPackageItem> findByPatientPackageIdIn(Collection<Long> patientPackageIds);

    /** Locks the row so two users cannot use the last quantity at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PatientPackageItem i where i.id = :id")
    Optional<PatientPackageItem> findByIdForUpdate(@Param("id") Long id);
}
