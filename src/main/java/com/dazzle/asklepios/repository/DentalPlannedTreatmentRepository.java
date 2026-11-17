package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.repository.projection.VisitNumberView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface DentalPlannedTreatmentRepository
        extends JpaRepository<DentalPlannedTreatment, Long>, JpaSpecificationExecutor<DentalPlannedTreatment>,
        DentalPlannedTreatmentRepositoryCustom {

    List<DentalPlannedTreatment> findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(Long patientId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(Long encounterId, PlannedTreatmentType type);

    List<DentalPlannedTreatment> findByToothActionIdAndCancelledFalse(Long toothActionId);

    /** Row lock for serialization. Ordinary {@code findById} calls do not lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DentalPlannedTreatment> findForUpdateById(Long id);

    List<DentalPlannedTreatment> findByTreatmentPlanId(Long treatmentPlanId);

    /**
     * Row holding the highest Visit Number for the patient across all encounters, DRAFT/CURRENT, including cancelled.
     */
    Optional<DentalPlannedTreatment> findFirstByPatientIdAndVisitNumberNotNullOrderByVisitNumberDesc(Long patientId);

    List<VisitNumberView> findDistinctByPatientIdAndVisitNumberNotNull(Long patientId);
}
