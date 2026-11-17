package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ToothAction;
import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.repository.projection.DentalChartProjections;
import com.dazzle.asklepios.repository.projection.VisitNumberView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToothActionRepository extends JpaRepository<ToothAction, Long> {

    List<ToothAction> findByToothIdAndCancelledFalse(Long toothId);

    List<ToothAction> findByToothIdInAndCancelledFalse(List<Long> toothIds);

    Optional<ToothAction> findByToothIdAndDentalActionIdAndSurfaceAndCancelledFalse(Long toothId, Long dentalActionId, ToothSurface surface);

    Optional<ToothAction> findBySourcePlannedTreatmentIdAndToothIdAndCancelledFalse(Long sourcePlannedTreatmentId, Long toothId);

    List<ToothAction> findBySourcePlannedTreatmentIdAndCancelledFalse(Long sourcePlannedTreatmentId);

    List<DentalChartProjections.ToothIdView> findDistinctByToothIdInAndCancelledFalse(Collection<Long> toothIds);

    Optional<ToothAction> findFirstByToothIdInAndVisitNumberNotNullOrderByVisitNumberDesc(List<Long> toothIds);

    List<VisitNumberView> findDistinctByToothIdInAndVisitNumberNotNull(List<Long> toothIds);
}
