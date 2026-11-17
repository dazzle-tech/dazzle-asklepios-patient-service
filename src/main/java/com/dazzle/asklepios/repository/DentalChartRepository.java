package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DentalChartRepository extends JpaRepository<DentalChart, Long> {

    /** All charts of an encounter, oldest id first. One is expected; the service rejects several. */
    List<DentalChart> findAllByEncounter_IdOrderByIdAsc(Long encounterId);

    /** Row lock that serializes inventory saves with clinical writes on the same chart. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select dc from DentalChart dc where dc.id = :id")
    Optional<DentalChart> findByIdForUpdate(@Param("id") Long id);

    /**
     * Atomic compare-and-set of the chart version. Returns 0 when the chart changed since the client
     * read it, which the caller reports as a stale save. Concurrent saves serialize on the row lock.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DentalChart dc
               set dc.version = dc.version + 1
             where dc.id = :id
               and dc.version = :expectedVersion
            """)
    int bumpVersionIfCurrent(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

    List<DentalChart> findByPatient_IdAndIdNotOrderByCreatedDateDesc(Long patientId, Long excludeId);

    /**
     * Charts of earlier, finished, non-cancelled encounters of the same patient, newest first.
     * Chronology is the encounter date, then the encounter id (ids grow with creation), then the
     * chart id. The current encounter is excluded; same-day encounters count only when older.
     */
    @Query("""
            select dc
            from DentalChart dc
            join dc.encounter e
            where dc.patient.id = :patientId
              and e.id <> :encounterId
              and e.status in :statuses
              and e.encounterDate is not null
              and (e.encounterDate < :asOfDate or (e.encounterDate = :asOfDate and e.id < :encounterId))
            order by e.encounterDate desc, e.id desc, dc.id desc
            """)
    List<DentalChart> findEligiblePreviousCharts(
            @Param("patientId") Long patientId,
            @Param("encounterId") Long encounterId,
            @Param("asOfDate") LocalDate asOfDate,
            @Param("statuses") Collection<TreatmentStatus> statuses,
            Pageable pageable
    );
}
