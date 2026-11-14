package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Default services of a dental visit go to Waseel with the held dental treatment. */
class DentalPreAuthorizationJoinTest {

    private PatientInsurance insurance;

    @BeforeEach
    void setUp() {
        insurance = new PatientInsurance();
        insurance.setId(128L);
    }

    @Test
    void defaultServicesJoinAHeldDentalTreatment() {
        PatientServiceAndProduct consultation = defaultService(1495L, PaymentStatus.PENDING);

        assertThat(EligibilityContractApprovalService.joinsDentalPreAuthorization(
                consultation, insurance, List.of(consultation, heldDentalTreatment(1496L)))).isTrue();
    }

    @Test
    void defaultServicesStayWhenNoDentalTreatmentIsHeld() {
        PatientServiceAndProduct consultation = defaultService(1495L, PaymentStatus.PENDING);
        PatientServiceAndProduct dental = heldDentalTreatment(1496L);
        dental.setPreAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED);

        assertThat(EligibilityContractApprovalService.joinsDentalPreAuthorization(
                consultation, insurance, List.of(consultation, dental))).isFalse();
    }

    @Test
    void aDentalRequestAlreadySentDoesNotPullNewDefaultServices() {
        PatientServiceAndProduct consultation = defaultService(1495L, PaymentStatus.PENDING);
        PatientServiceAndProduct dental = heldDentalTreatment(1496L);
        dental.setPreAuthorizationRequestId(77L);

        assertThat(EligibilityContractApprovalService.joinsDentalPreAuthorization(
                consultation, insurance, List.of(consultation, dental))).isFalse();
    }

    @Test
    void reservedOrBilledDefaultServicesAreNotPulledBack() {
        PatientServiceAndProduct reserved = defaultService(1495L, PaymentStatus.RESERVED);
        PatientServiceAndProduct billed = defaultService(1494L, PaymentStatus.PENDING);
        billed.setIsBilled(true);
        List<PatientServiceAndProduct> visit = List.of(reserved, billed, heldDentalTreatment(1496L));

        assertThat(EligibilityContractApprovalService.joinsDentalPreAuthorization(reserved, insurance, visit)).isFalse();
        assertThat(EligibilityContractApprovalService.joinsDentalPreAuthorization(billed, insurance, visit)).isFalse();
    }

    private PatientServiceAndProduct defaultService(Long id, PaymentStatus paymentStatus) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceSource(ServiceSource.ENCOUNTER_DEFAULT_SERVICE)
                .patientInsuranceId(insurance.getId())
                .preAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED)
                .paymentStatus(paymentStatus)
                .isBilled(false)
                .build();
        line.setId(id);
        return line;
    }

    private PatientServiceAndProduct heldDentalTreatment(Long id) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .billingItemType(BillingItemTypes.PROCEDURE)
                .serviceSource(ServiceSource.DENTAL_PROCEDURE)
                .patientInsuranceId(insurance.getId())
                .preAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL)
                .paymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH)
                .isBilled(false)
                .build();
        line.setId(id);
        return line;
    }
}
