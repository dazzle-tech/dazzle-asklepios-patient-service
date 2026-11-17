package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DentalChartRepository extends JpaRepository<DentalChart, Long>, JpaSpecificationExecutor<DentalChart> {

    List<DentalChart> findByPatient_Id(Long patientId);

    List<DentalChart> findAllByEncounter_IdOrderByIdAsc(Long encounterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dc from DentalChart dc where dc.id = :id")
    Optional<DentalChart> findByIdForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DentalChart dc
               set dc.version = dc.version + 1
             where dc.id = :id
               and dc.version = :expectedVersion
            """)
    int bumpVersionIfCurrent(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    List<DentalChart> findByPatient_Id(Long patientId);

    List<DentalChart> findByPatient_IdAndIdNotOrderByCreatedDateDesc(Long patientId, Long excludeId);
}
