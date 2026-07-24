package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientServiceAndProductRepository extends JpaRepository<PatientServiceAndProduct, Long> {

    Page<PatientServiceAndProduct> findAllByEncounterId(Long encounterId, Pageable pageable);

    Page<PatientServiceAndProduct> findAllByPatientId(Long patientId, Pageable pageable);

    boolean existsByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledTrue(
            Long patientId,
            Long encounterId,
            Long diagnosticTestId
    );

    int deleteByPatientIdAndEncounterIdAndDiagnosticTestIdAndIsBilledFalse(
            Long patientId,
            Long encounterId,
            Long diagnosticTestId
    );

    Page<PatientServiceAndProduct> findAllByEncounterIdAndServiceSourceAndSourceId(
            Long encounterId,
            ServiceSource serviceSource,
            Long sourceId,
            Pageable pageable
    );

    Optional<PatientServiceAndProduct> findByServiceSourceAndSourceIdAndBillingItemType(
            ServiceSource serviceSource,
            Long sourceId,
            BillingItemTypes billingItemType
    );

    List<PatientServiceAndProduct> findByEncounterId(Long encounterId);

    List<PatientServiceAndProduct> findByEncounterIdAndPreAuthorizationStatus(
            Long encounterId,
            PreAuthorizationStatus preAuthorizationStatus
    );

    List<PatientServiceAndProduct> findByPaymentId(Long paymentId);

    List<PatientServiceAndProduct> findByEncounterIdAndPaymentIdIsNull(Long encounterId);

    List<PatientServiceAndProduct> findByEncounterIdAndPaymentId(
            Long encounterId,
            Long paymentId
    );

    Optional<PatientServiceAndProduct>
    findFirstByEncounterIdAndServiceIdAndIsDefaultServiceTrue(
            Long encounterId,
            Long serviceId
    );

    List<PatientServiceAndProduct>
    findAllByEncounterIdAndIsDefaultServiceTrueOrderByIdAsc(
            Long encounterId
    );

    boolean existsByEncounterIdAndServiceIdAndIsDefaultServiceTrue(
            Long encounterId,
            Long serviceId
    );
}