package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PatientServiceAndProductRepository
        extends JpaRepository<PatientServiceAndProduct, Long> {

    Page<PatientServiceAndProduct> findAllByEncounterId(Long encounterId, Pageable pageable);

    Page<PatientServiceAndProduct> findAllByEncounterIdAndPaymentStatusNot(
            Long encounterId,
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    Page<PatientServiceAndProduct> findAllByPatientId(Long patientId, Pageable pageable);

    Page<PatientServiceAndProduct> findAllByPatientIdAndPaymentStatusNot(
            Long patientId,
            PaymentStatus paymentStatus,
            Pageable pageable
    );

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

    Page<PatientServiceAndProduct> findAllByEncounterIdAndServiceSourceAndSourceIdAndPaymentStatusNot(
            Long encounterId,
            ServiceSource serviceSource,
            Long sourceId,
            PaymentStatus paymentStatus,
            Pageable pageable
    );

    Optional<PatientServiceAndProduct> findByServiceSourceAndSourceIdAndBillingItemType(
            ServiceSource serviceSource,
            Long sourceId,
            BillingItemTypes billingItemType
    );

    List<PatientServiceAndProduct> findByEncounterId(Long encounterId);

    long countByEncounterId(Long encounterId);

    List<PatientServiceAndProduct> findByEncounterIdAndPreAuthorizationStatus(
            Long encounterId,
            PreAuthorizationStatus preAuthorizationStatus
    );

    /**
     * Items awaiting first Waseel submission for this encounter (not yet linked to a request).
     */
    List<PatientServiceAndProduct> findByEncounterIdAndPreAuthorizationStatusAndPreAuthorizationRequestIdIsNull(
            Long encounterId,
            PreAuthorizationStatus preAuthorizationStatus
    );

    boolean existsByEncounterIdAndPreAuthorizationStatus(
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
    findFirstByEncounterIdAndBillingItemTypeAndSourceIdAndIsDefaultServiceTrue(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long sourceId
    );

    boolean existsByEncounterIdAndPatientInsuranceIdIsNotNull(Long encounterId);

    Optional<PatientServiceAndProduct> findFirstByEncounterIdAndPatientInsuranceIdIsNotNullOrderByIdDesc(
            Long encounterId
    );

    boolean existsByEncounterIdAndBillingItemTypeAndBrandMedicationId(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long brandMedicationId
    );

    boolean existsByEncounterIdAndBillingItemTypeAndDiagnosticTestId(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long diagnosticTestId
    );

    boolean existsByEncounterIdAndBillingItemTypeAndServiceId(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long serviceId
    );

    boolean existsByEncounterIdAndBillingItemTypeAndProcedureId(
            Long encounterId,
            BillingItemTypes billingItemType,
            Long procedureId
    );

    List<PatientServiceAndProduct>
    findAllByEncounterIdAndIsDefaultServiceTrueOrderByIdAsc(
            Long encounterId
    );

    List<PatientServiceAndProduct> findByPreAuthorizationRequestId(Long preAuthorizationRequestId);

    boolean existsByPreAuthorizationRequestIdAndIsBilledFalse(Long preAuthorizationRequestId);
}
