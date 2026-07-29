package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingRequest;
import com.dazzle.asklepios.service.dto.billing.PreviewCatalogItemPricingResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CatalogItemPricingPreviewService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CatalogItemPricingPreviewService.class
            );

    private static final String ENTITY_NAME =
            "catalogItemPricingPreview";

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final BillingEngineService billingEngineService;

    @Transactional(readOnly = true)
    public PreviewCatalogItemPricingResult preview(
            PreviewCatalogItemPricingRequest request
    ) {
        validateRequest(request);

        Patient patient = loadPatient(request.patientId());
        PatientEncounter encounter = loadAndValidateEncounter(
                request,
                patient
        );
        BillingCoverageType coverageType =
                request.coverageType() == null
                        ? BillingCoverageType.SELF_PAY
                        : request.coverageType();
        PatientInsurance insurance =
                resolveInsurance(
                        request,
                        coverageType
                );

        PatientServiceAndProduct previewItem =
                buildPreviewItem(
                        request,
                        encounter,
                        insurance,
                        coverageType
                );

        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(
                        previewItem,
                        request.facilityId()
                );

        String priceListItemCode =
                resolvedPrice.pricingResponse() == null
                        ? null
                        : resolvedPrice
                        .pricingResponse()
                        .priceListItemCode();

        LOG.debug(
                "[PREVIEW_CATALOG_PRICING] encounterId={} itemType={} "
                        + "sourceId={} unitPrice={} setupUnitPrice={} source={}",
                request.encounterId(),
                request.billingItemType(),
                previewItem.getSourceId(),
                resolvedPrice.unitPrice(),
                resolvedPrice.setupUnitPrice(),
                resolvedPrice.priceSource()
        );

        return new PreviewCatalogItemPricingResult(
                resolvedPrice.setupUnitPrice(),
                resolvedPrice.unitPrice(),
                resolvedPrice.priceSource() == null
                        ? BillingPriceSource.SETUP_FALLBACK.name()
                        : resolvedPrice.priceSource().name(),
                priceListItemCode,
                resolvedPrice.currency() == null
                        ? request.currency()
                        : resolvedPrice.currency()
        );
    }

    private PatientServiceAndProduct buildPreviewItem(
            PreviewCatalogItemPricingRequest request,
            PatientEncounter encounter,
            PatientInsurance insurance,
            BillingCoverageType coverageType
    ) {
        Long sourceId = resolveSourceId(request);

        return PatientServiceAndProduct.builder()
                .patientId(request.patientId())
                .encounterId(encounter.getId())
                .billingItemType(request.billingItemType())
                .brandMedicationId(request.brandMedicationId())
                .diagnosticTestId(request.diagnosticTestId())
                .serviceId(request.serviceId())
                .procedureId(request.procedureId())
                .sourceId(sourceId)
                .serviceSource(ServiceSource.SERVICE_AND_PRODUCT)
                .quantity(
                        defaultQuantity(
                                request.quantity()
                        )
                )
                .unitPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .exemptionAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .patientShareAmount(BigDecimal.ZERO)
                .insuranceShareAmount(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.ZERO)
                .currency(request.currency())
                .isBilled(Boolean.FALSE)
                .paymentStatus(PaymentStatus.PENDING)
                .coverageStatus(
                        insurance == null
                                ? CoverageStatus.NOT_CHECKED
                                : CoverageStatus.COVERED
                )
                .patientInsuranceId(
                        insurance == null
                                ? null
                                : insurance.getId()
                )
                .isDefaultService(Boolean.FALSE)
                .isExempted(Boolean.FALSE)
                .preAuthorizationRequired(Boolean.FALSE)
                .notes("Catalog pricing preview")
                .build();
    }

    private void validateRequest(
            PreviewCatalogItemPricingRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Preview request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        validateItemReference(request);
    }

    private void validateItemReference(
            PreviewCatalogItemPricingRequest request
    ) {
        BillingItemTypes type = request.billingItemType();

        switch (type) {
            case MEDICATION -> {
                if (request.brandMedicationId() == null) {
                    throw new BadRequestAlertException(
                            "Medication is required.",
                            ENTITY_NAME,
                            "medication.required"
                    );
                }
            }
            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                if (request.diagnosticTestId() == null) {
                    throw new BadRequestAlertException(
                            "Diagnostic test is required.",
                            ENTITY_NAME,
                            "diagnosticTest.required"
                    );
                }
            }
            case SERVICE -> {
                if (request.serviceId() == null) {
                    throw new BadRequestAlertException(
                            "Service is required.",
                            ENTITY_NAME,
                            "service.required"
                    );
                }
            }
            case PROCEDURE -> {
                if (request.procedureId() == null) {
                    throw new BadRequestAlertException(
                            "Procedure is required.",
                            ENTITY_NAME,
                            "procedure.required"
                    );
                }
            }
            default -> throw new BadRequestAlertException(
                    "Unsupported catalog item type.",
                    ENTITY_NAME,
                    "billingItemType.unsupported"
            );
        }
    }

    private Long resolveSourceId(
            PreviewCatalogItemPricingRequest request
    ) {
        return switch (request.billingItemType()) {
            case SERVICE -> request.serviceId();
            case PROCEDURE -> request.procedureId();
            case MEDICATION -> request.brandMedicationId();
            case LABORATORY, RADIOLOGY, PATHOLOGY ->
                    request.diagnosticTestId();
        };
    }

    private Long defaultQuantity(
            BigDecimal quantity
    ) {
        if (quantity == null || quantity.signum() <= 0) {
            return 1L;
        }

        return quantity.longValue();
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository
                .findById(patientId)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + patientId,
                                ENTITY_NAME,
                                "patient.notfound"
                        )
                );
    }

    private PatientEncounter loadAndValidateEncounter(
            PreviewCatalogItemPricingRequest request,
            Patient patient
    ) {
        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(request.encounterId())
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + request.encounterId(),
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        if (encounter.getPatient() == null
                || !patient.getId().equals(encounter.getPatient().getId())) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to the selected patient.",
                    ENTITY_NAME,
                    "encounter.patient.mismatch"
            );
        }

        if (!request.facilityId().equals(encounter.getFacilityId())) {
            throw new BadRequestAlertException(
                    "Encounter facility does not match the request facility.",
                    ENTITY_NAME,
                    "encounter.facility.mismatch"
            );
        }

        return encounter;
    }

    private PatientInsurance resolveInsurance(
            PreviewCatalogItemPricingRequest request,
            BillingCoverageType coverageType
    ) {
        if (coverageType == BillingCoverageType.SELF_PAY) {
            if (request.patientInsuranceId() != null) {
                throw new BadRequestAlertException(
                        "Patient insurance must be null for self-pay coverage.",
                        ENTITY_NAME,
                        "patientInsurance.mustBeNull"
                );
            }

            return null;
        }

        if (request.patientInsuranceId() == null) {
            throw new BadRequestAlertException(
                    "Patient insurance is required for insurance coverage.",
                    ENTITY_NAME,
                    "patientInsurance.required"
            );
        }

        return patientInsuranceRepository
                .findByIdAndPatient_IdAndExpirationDateGreaterThanEqual(
                        request.patientInsuranceId(),
                        request.patientId(),
                        LocalDate.now()
                )
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "A valid, non-expired insurance record was not found for this patient.",
                                ENTITY_NAME,
                                "patientInsurance.invalid"
                        )
                );
    }
}
