package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.repository.projection.VisitNumberView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DentalPlannedTreatmentRepository
        extends JpaRepository<DentalPlannedTreatment, Long>, JpaSpecificationExecutor<DentalPlannedTreatment> {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DentalPlannedTreatment t where t.id = :id")
    Optional<DentalPlannedTreatment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p.toothId, count(p)
            from DentalPlannedTreatment p
            where p.toothId in :toothIds
              and p.cancelled = false
              and p.status <> :completedStatus
            group by p.toothId
            """)
    List<Object[]> countOpenByToothIds(
            @Param("toothIds") Collection<Long> toothIds,
            @Param("completedStatus") PlannedTreatmentStatus completedStatus
    );

    List<DentalPlannedTreatment> findByTreatmentPlanId(Long treatmentPlanId);

    /**
     * Row holding the highest Visit Number for the patient across all encounters, DRAFT/CURRENT, including cancelled.
     */
    Optional<DentalPlannedTreatment> findFirstByPatientIdAndVisitNumberNotNullOrderByVisitNumberDesc(Long patientId);

    List<VisitNumberView> findDistinctByPatientIdAndVisitNumberNotNull(Long patientId);
}
