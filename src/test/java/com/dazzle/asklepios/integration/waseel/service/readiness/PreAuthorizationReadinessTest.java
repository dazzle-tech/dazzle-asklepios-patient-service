package com.dazzle.asklepios.integration.waseel.service.readiness;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreAuthorizationReadinessTest {

    private static final List<String> CLINICAL = List.of(
            "chief-complaint", "history-of-present-illness", "patient-history",
            "physical-examination", "treatment-plan", "investigation-result");

    private static final List<String> VITALS = List.of(
            "temperature", "pulse", "oxygen-saturation", "respiratory-rate",
            "vital-sign-systolic", "vital-sign-diastolic", "vital-sign-height", "vital-sign-weight");

    @Test
    void dentalTreatmentIsAnOralClaim() {
        assertThat(PreAuthorizationReadiness.claimTypeOf(EncounterType.CLINIC, List.of(
                line(ServiceSource.ENCOUNTER_DEFAULT_SERVICE), line(ServiceSource.DENTAL_PROCEDURE))))
                .isEqualTo(PreAuthorizationClaimType.ORAL_OP);
        assertThat(PreAuthorizationReadiness.claimTypeOf(EncounterType.CLINIC, List.of(line(ServiceSource.SERVICE_AND_PRODUCT))))
                .isEqualTo(PreAuthorizationClaimType.PROFESSIONAL_OP);
        assertThat(PreAuthorizationReadiness.claimTypeOf(EncounterType.EMERGENCY, List.of(line(ServiceSource.SERVICE_AND_PRODUCT))))
                .isEqualTo(PreAuthorizationClaimType.PROFESSIONAL_EMERGENCY);
    }

    @Test
    void aVisitWithNothingFilledMissesEveryProfessionalField() {
        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.PROFESSIONAL_OP, false, List.of(), 1);

        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.missingFields()).hasSize(15);
        assertThat(readiness.missingFields().get(0).code()).isEqualTo(PreAuthorizationRequiredField.DIAGNOSIS);
        assertThat(readiness.missingFieldsMessage()).startsWith("Fill these fields before the pre-authorization can be sent to Waseel: Diagnosis (Diagnosis)");
    }

    @Test
    void aDentalClaimDoesNotAskForVitalSigns() {
        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.ORAL_OP, true, info(CLINICAL), 1);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.missingFields()).isEmpty();
    }

    @Test
    void aProfessionalClaimNeedsTheVitalSignsToo() {
        PreAuthorizationReadiness withoutVitals = PreAuthorizationReadiness.of(
                1L, PreAuthorizationClaimType.PROFESSIONAL_OP, true, info(CLINICAL), 1);

        assertThat(withoutVitals.missingFields())
                .extracting(PreAuthorizationReadiness.MissingField::code)
                .containsExactly(
                        PreAuthorizationRequiredField.TEMPERATURE,
                        PreAuthorizationRequiredField.PULSE,
                        PreAuthorizationRequiredField.OXYGEN_SATURATION,
                        PreAuthorizationRequiredField.RESPIRATORY_RATE,
                        PreAuthorizationRequiredField.SYSTOLIC,
                        PreAuthorizationRequiredField.DIASTOLIC,
                        PreAuthorizationRequiredField.HEIGHT,
                        PreAuthorizationRequiredField.WEIGHT);

        List<String> all = new ArrayList<>(CLINICAL);
        all.addAll(VITALS);
        assertThat(PreAuthorizationReadiness.of(1L, PreAuthorizationClaimType.PROFESSIONAL_OP, true, info(all), 1).ready())
                .isTrue();
    }

    @Test
    void aMissingDiagnosisIsAlwaysReported() {
        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.ORAL_OP, false, info(CLINICAL), 1);

        assertThat(readiness.missingFields())
                .extracting(PreAuthorizationReadiness.MissingField::code)
                .containsExactly(PreAuthorizationRequiredField.DIAGNOSIS);
    }

    @Test
    void anEmptyValueDoesNotCount() {
        WaseelApprovalSupportingInfo empty = new WaseelApprovalSupportingInfo(
                1, "chief-complaint", null, null, null, "  ", null, null, null, null, null, null);

        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.ORAL_OP, true, List.of(empty), 1);

        assertThat(readiness.missingFields())
                .extracting(PreAuthorizationReadiness.MissingField::code)
                .contains(PreAuthorizationRequiredField.CHIEF_COMPLAINT);
    }

    @Test
    void aCodedInvestigationResultCounts() {
        WaseelApprovalSupportingInfo investigation = new WaseelApprovalSupportingInfo(
                1, "investigation-result", "INP", null, null, null, null, null, null, null, null, null);

        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.ORAL_OP, true, List.of(investigation), 1);

        assertThat(readiness.missingFields())
                .extracting(PreAuthorizationReadiness.MissingField::code)
                .doesNotContain(PreAuthorizationRequiredField.INVESTIGATION_RESULT);
    }

    @Test
    void theErrorListsTheMissingFieldsAndKeepsThemWhenRewrapped() {
        PreAuthorizationReadiness readiness = PreAuthorizationReadiness.of(
                526L, PreAuthorizationClaimType.ORAL_OP, false, info(CLINICAL), 1);

        BadRequestAlertException error = readiness.toMissingFieldsException("preAuthorization");
        BadRequestAlertException rewrapped = new BadRequestAlertException(
                readiness.missingFieldsMessage(), "dentalProcedure", PreAuthorizationReadiness.MISSING_FIELDS_ERROR_KEY);
        PreAuthorizationReadiness.carryMissingFields(error, rewrapped);

        assertThat(error.getErrorKey()).isEqualTo(PreAuthorizationReadiness.MISSING_FIELDS_ERROR_KEY);
        assertThat(rewrapped.getBody().getProperties())
                .containsEntry(PreAuthorizationReadiness.CLAIM_TYPE_PROPERTY, "ORAL_OP")
                .containsEntry(PreAuthorizationReadiness.MISSING_FIELDS_PROPERTY, readiness.missingFields());
    }

    private static List<WaseelApprovalSupportingInfo> info(List<String> categories) {
        return categories.stream()
                .map(category -> new WaseelApprovalSupportingInfo(
                        1, category, null, null, null, "value", null, null, null, null, null, null))
                .toList();
    }

    private static PatientServiceAndProduct line(ServiceSource source) {
        return PatientServiceAndProduct.builder().serviceSource(source).build();
    }
}
