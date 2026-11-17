package com.dazzle.asklepios.integration.waseel.service.readiness;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.web.ErrorResponseException;

import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a visit has everything Waseel needs to receive a pre-authorization, and what is missing.
 */
public record PreAuthorizationReadiness(
        Long encounterId,
        PreAuthorizationClaimType claimType,
        boolean ready,
        List<MissingField> missingFields,
        int waitingItems
) implements Serializable {

    public static final String MISSING_FIELDS_ERROR_KEY = "preAuthorization.missingFields";
    /** Error body properties the UI reads to list the missing fields in a dialog. */
    static final String MISSING_FIELDS_PROPERTY = "missingFields";
    static final String CLAIM_TYPE_PROPERTY = "claimType";

    private static final Set<ServiceSource> DENTAL_SOURCES = EnumSet.of(
            ServiceSource.DENTAL_PROCEDURE,
            ServiceSource.DENTAL_CHART_CDT_TREATMENT,
            ServiceSource.DENTAL_CHART_MANUAL_SERVICE
    );

    public record MissingField(PreAuthorizationRequiredField code, String label, String section) implements Serializable {

        static MissingField of(PreAuthorizationRequiredField field) {
            return new MissingField(field, field.label(), field.section());
        }
    }

    public static PreAuthorizationReadiness of(
            Long encounterId,
            PreAuthorizationClaimType claimType,
            boolean hasDiagnosis,
            Collection<WaseelApprovalSupportingInfo> supportingInfo,
            int waitingItems
    ) {
        List<MissingField> missing = missingFields(claimType, hasDiagnosis, supportingInfo);
        return new PreAuthorizationReadiness(encounterId, claimType, missing.isEmpty(), missing, waitingItems);
    }

    /** Dental treatment makes it a dental claim; an emergency visit an emergency one; else professional OP. */
    public static PreAuthorizationClaimType claimTypeOf(EncounterType encounterType, Collection<PatientServiceAndProduct> items) {
        boolean dental = items != null && items.stream()
                .filter(Objects::nonNull)
                .anyMatch(item -> DENTAL_SOURCES.contains(item.getServiceSource()));
        if (dental) {
            return PreAuthorizationClaimType.ORAL_OP;
        }
        return encounterType == EncounterType.EMERGENCY
                ? PreAuthorizationClaimType.PROFESSIONAL_EMERGENCY
                : PreAuthorizationClaimType.PROFESSIONAL_OP;
    }

    /** The required fields of the claim type that the visit does not provide, in the claim type order. */
    static List<MissingField> missingFields(
            PreAuthorizationClaimType claimType,
            boolean hasDiagnosis,
            Collection<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        Set<String> provided = supportingInfo == null
                ? Set.of()
                : supportingInfo.stream()
                        .filter(PreAuthorizationReadiness::hasContent)
                        .map(info -> info.category().trim())
                        .collect(Collectors.toSet());

        return claimType.requiredFields().stream()
                .filter(field -> field == PreAuthorizationRequiredField.DIAGNOSIS
                        ? !hasDiagnosis
                        : !provided.contains(field.category()))
                .map(MissingField::of)
                .toList();
    }

    /**
     * The error that stops the submission. Besides the message, its body lists the missing fields
     * ({@code missingFields}: code, label, section) and the claim type, so the UI can show them.
     */
    public BadRequestAlertException toMissingFieldsException(String entityName) {
        BadRequestAlertException exception =
                new BadRequestAlertException(missingFieldsMessage(), entityName, MISSING_FIELDS_ERROR_KEY);
        exception.getBody().setProperty(MISSING_FIELDS_PROPERTY, missingFields);
        exception.getBody().setProperty(CLAIM_TYPE_PROPERTY, claimType.name());
        return exception;
    }

    /** Keeps the missing-field details when a caller re-wraps the error under its own entity. */
    public static void carryMissingFields(ErrorResponseException source, ErrorResponseException target) {
        Map<String, Object> properties = source.getBody().getProperties();
        if (properties == null || target == null) {
            return;
        }
        for (String property : List.of(MISSING_FIELDS_PROPERTY, CLAIM_TYPE_PROPERTY)) {
            if (properties.containsKey(property)) {
                target.getBody().setProperty(property, properties.get(property));
            }
        }
    }

    /** The message shown to the user when the pre-authorization cannot be sent. */
    public String missingFieldsMessage() {
        return "Fill these fields before the pre-authorization can be sent to Waseel: "
                + missingFields.stream()
                        .map(field -> field.label() + " (" + field.section() + ")")
                        .collect(Collectors.joining(", "))
                + ".";
    }

    private static boolean hasContent(WaseelApprovalSupportingInfo info) {
        return info != null
                && info.category() != null
                && (isNotBlank(info.value()) || isNotBlank(info.code()) || isNotBlank(info.attachment()));
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
