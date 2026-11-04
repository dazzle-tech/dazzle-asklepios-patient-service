package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DentalChartRepository extends JpaRepository<DentalChart, Long> {

    Optional<DentalChart> findByEncounter_Id(Long encounterId);

    List<DentalChart> findByPatient_IdAndIdNotOrderByCreatedDateDesc(Long patientId, Long excludeId);

    @Query("select dc from DentalChart dc where dc.patient.id = :patientId and dc.id <> :excludeChartId " +
            "and dc.encounter.status = com.dazzle.asklepios.domain.enumeration.TreatmentStatus.CLOSED " +
            "order by dc.createdDate desc")
    List<DentalChart> findMostRecentClosedEncounterChart(@Param("patientId") Long patientId, @Param("excludeChartId") Long excludeChartId, Pageable pageable);
}
