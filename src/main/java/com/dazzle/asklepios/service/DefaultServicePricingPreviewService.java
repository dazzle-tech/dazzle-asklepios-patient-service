package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.billing.BillingPricingInput;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServiceItem;
import com.dazzle.asklepios.service.dto.billing.PreviewDefaultServicePricingResult;
import com.dazzle.asklepios.service.dto.billing.PreviewDefaultServicesPricingRequest;
import com.dazzle.asklepios.service.dto.billing.PreviewDefaultServicesPricingResult;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DefaultServicePricingPreviewService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    DefaultServicePricingPreviewService.class
            );

    private static final String ENTITY_NAME =
            "defaultServiceBilling";

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final ServiceClient serviceClient;
    private final BillingEngineService billingEngineService;
    private final BillingPricingInputFactory billingPricingInputFactory;
    private final BillingPricingService billingPricingService;
    private final InsurancePatientShareCalculator insurancePatientShareCalculator;
    private final EncounterCoverageService encounterCoverageService;

    @Transactional
    public PreviewDefaultServicesPricingResult preview(
            Long encounterId,
            PreviewDefaultServicesPricingRequest request
    ) {
        validateRequest(encounterId, request);

        Patient patient = loadPatient(request.patientId());
        PatientEncounter encounter = loadAndValidateEncounter(
                encounterId,
                request,
                patient
        );
        PatientInsurance insurance = resolveInsurance(request);

        if (request.coverageType() == BillingCoverageType.INSURANCE
                && insurance != null) {
            encounterCoverageService.applyCoverage(
                    encounter,
                    BillingCoverageType.INSURANCE,
                    insurance.getId()
            );

            LOG.info(
                    "[PREVIEW_PRICING] Applied insurance coverage encounterId={} patientInsuranceId={} payorId={} payerNphiesId={}",
                    encounterId,
                    insurance.getId(),
                    insurance.getPayorId(),
                    insurance.getPayerNphiesId()
            );
        } else if (request.coverageType() == BillingCoverageType.SELF_PAY) {
            encounterCoverageService.applyCoverage(
                    encounter,
                    BillingCoverageType.SELF_PAY,
                    null
            );
        }

        List<PrepareDefaultServiceItem> orderedItems =
                request.items()
                        .stream()
                        .sorted(Comparator.comparing(
                                PrepareDefaultServiceItem::sequence
                        ))
                        .toList();

        List<PreviewDefaultServicePricingResult> itemResults =
                new ArrayList<>();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (PrepareDefaultServiceItem requestedItem : orderedItems) {
            ServiceSetupDTO service = loadAndValidateSetupService(
                    requestedItem.serviceId(),
                    request.facilityId()
            );

            PatientServiceAndProduct previewItem =
                    buildPreviewItem(
                            encounter,
                            request,
                            requestedItem,
                            insurance,
                            service
                    );

            PreviewDefaultServicePricingResult itemResult =
                    resolvePreviewForItem(
                            previewItem,
                            request.facilityId(),
                            requestedItem,
                            insurance,
                            service.category()
                    );

            itemResults.add(itemResult);

            totalGross = totalGross.add(
                    defaultZero(itemResult.grossAmount())
            );
            totalDiscount = totalDiscount.add(
                    defaultZero(itemResult.discountAmount())
            );
            totalTax = totalTax.add(
                    defaultZero(itemResult.taxAmount())
            );
            totalNet = totalNet.add(
                    defaultZero(itemResult.netAmount())
            );
        }

        LOG.debug(
                "[PREVIEW_PRICING] encounterId={} itemCount={} net={}",
                encounterId,
                itemResults.size(),
                totalNet
        );

        return new PreviewDefaultServicesPricingResult(
                request.patientId(),
                encounterId,
                request.facilityId(),
                request.coverageType(),
                insurance == null ? null : insurance.getId(),
                request.currency(),
                totalGross,
                totalDiscount,
                totalTax,
                totalNet,
                List.copyOf(itemResults)
        );
    }

    private PreviewDefaultServicePricingResult resolvePreviewForItem(
            PatientServiceAndProduct previewItem,
            Long facilityId,
            PrepareDefaultServiceItem requestedItem,
            PatientInsurance insurance,
            String serviceCategory
    ) {
        ResolvedBillingPrice resolvedPrice =
                billingEngineService.resolvePricing(
                        previewItem,
                        facilityId
                );

        BillingPricingInput pricingInput =
                billingPricingInputFactory.create(
                        previewItem,
                        resolvedPrice
                );

        BillingProcessingContext context =
                BillingProcessingContext.builder()
                        .patientServiceProduct(previewItem)
                        .pricingInput(pricingInput)
                        .build();

        billingPricingService.calculate(context);

        PriceCalculationResult pricing =
                context.getPricingResult();

        if (insurance != null
                && resolvedPrice.priceSource() == BillingPriceSource.SETUP_FALLBACK) {
            LOG.warn(
                    "[PREVIEW_PRICING] Insurance visit resolved to setup fallback "
                            + "serviceId={} patientInsuranceId={} payorId={} setupUnitPrice={} resolvedUnitPrice={}",
                    requestedItem.serviceId(),
                    insurance.getId(),
                    insurance.getPayorId(),
                    resolvedPrice.setupUnitPrice(),
                    resolvedPrice.unitPrice()
            );
        } else {
            LOG.info(
                    "[PREVIEW_PRICING] serviceId={} priceSource={} setupUnitPrice={} resolvedUnitPrice={} netAmount={}",
                    requestedItem.serviceId(),
                    resolvedPrice.priceSource(),
                    resolvedPrice.setupUnitPrice(),
                    pricing.unitPrice(),
                    pricing.netAmount()
            );
        }

        String priceSource =
                pricingInput.priceSource() == null
                        ? BillingPriceSource.SETUP_FALLBACK.name()
                        : pricingInput.priceSource().name();

        BigDecimal patientShareAmount = BigDecimal.ZERO;
        BigDecimal insuranceShareAmount = BigDecimal.ZERO;

        if (insurance != null && !Boolean.TRUE.equals(previewItem.getIsExempted())) {
            InsuranceSplit split =
                    insurancePatientShareCalculator.calculateSplit(
                            insurance,
                            serviceCategory,
                            previewItem.getServiceSource(),
                            pricing.netAmount()
                    );
            patientShareAmount = split.patientShare();
            insuranceShareAmount = split.insuranceShare();
        } else if (!Boolean.TRUE.equals(previewItem.getIsExempted())) {
            patientShareAmount = pricing.netAmount();
        }

        return new PreviewDefaultServicePricingResult(
                requestedItem.serviceId(),
                requestedItem.sequence(),
                resolvedPrice.setupUnitPrice(),
                pricing.unitPrice(),
                pricing.grossAmount(),
                pricing.discountAmount(),
                pricing.taxAmount(),
                pricing.netAmount(),
                priceSource,
                pricingInput.priceListItemCode(),
                patientShareAmount,
                insuranceShareAmount
        );
    }

    private PatientServiceAndProduct buildPreviewItem(
            PatientEncounter encounter,
            PreviewDefaultServicesPricingRequest request,
            PrepareDefaultServiceItem requestedItem,
            PatientInsurance insurance,
            ServiceSetupDTO service
    ) {
        return PatientServiceAndProduct.builder()
                .patientId(request.patientId())
                .encounterId(encounter.getId())
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceId(requestedItem.serviceId())
                .sourceId(requestedItem.serviceId())
                .serviceSource(ServiceSource.ENCOUNTER_DEFAULT_SERVICE)
                .quantity(requestedItem.quantity())
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
                .isDefaultService(Boolean.TRUE)
                .isExempted(requestedItem.exempted())
                .preAuthorizationRequired(Boolean.FALSE)
                .notes("Pricing preview: " + service.name())
                .build();
    }

    private void validateRequest(
            Long encounterId,
            PreviewDefaultServicesPricingRequest request
    ) {
        if (encounterId == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounterId.required"
            );
        }

        if (request == null) {
            throw new BadRequestAlertException(
                    "Preview request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one service item is required.",
                    ENTITY_NAME,
                    "items.required"
            );
        }
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
            Long encounterId,
            PreviewDefaultServicesPricingRequest request,
            Patient patient
    ) {
        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id "
                                                + encounterId,
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
            PreviewDefaultServicesPricingRequest request
    ) {
        if (request.coverageType()
                == com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType.SELF_PAY) {
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

    private ServiceSetupDTO loadAndValidateSetupService(
            Long serviceId,
            Long facilityId
    ) {
        try {
            ServiceSetupDTO service =
                    serviceClient.getServiceDetails(serviceId);

            if (service == null || service.id() == null) {
                throw new NotFoundAlertException(
                        "Setup service not found with id " + serviceId,
                        ENTITY_NAME,
                        "service.notfound"
                );
            }

            return service;
        } catch (FeignException.NotFound exception) {
            throw new NotFoundAlertException(
                    "Setup service not found with id " + serviceId,
                    ENTITY_NAME,
                    "service.notfound"
            );
        }
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
