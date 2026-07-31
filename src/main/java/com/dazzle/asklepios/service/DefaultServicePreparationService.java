package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingPricingSnapshot;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPricingSnapshotStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterPreAuthorizationSyncService;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingPricingSnapshotRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServiceItem;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesRequest;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesResult;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DefaultServicePreparationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DefaultServicePreparationService.class);

    private static final String ENTITY_NAME =
            "defaultServiceBilling";

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_CHARGE_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;
    private final BillingPricingSnapshotRepository billingPricingSnapshotRepository;
    private final ServiceClient serviceClient;
    private final BillingEngineService billingEngineService;
    private final PreAuthorizationResolutionService preAuthorizationResolutionService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;

    /**
     * Creates/reuses selected default-service PSP records and sends each one
     * through the Billing Engine. This operation does not create a payment,
     * allocation, consumption, checkout, or invoice closure.
     */
    @Transactional
    public PrepareDefaultServicesResult prepare(
            Long encounterId,
            PrepareDefaultServicesRequest request
    ) {
        validateRequest(encounterId, request);

        Patient patient = loadPatient(request.patientId());
        PatientEncounter encounter = loadAndValidateEncounter(
                encounterId,
                request,
                patient
        );

        PatientInsurance insurance = resolveInsurance(request);

        List<PrepareDefaultServiceItem> orderedItems =
                request.items()
                        .stream()
                        .sorted(Comparator.comparing(
                                PrepareDefaultServiceItem::sequence
                        ))
                        .toList();

        List<PrepareDefaultServicesResult.PreparedDefaultServiceResult> results =
                new ArrayList<>();

        for (PrepareDefaultServiceItem requestedItem : orderedItems) {
            ServiceSetupDTO service = loadAndValidateSetupService(
                    requestedItem.serviceId(),
                    request.facilityId()
            );

            PatientServiceAndProduct item =
                    findOrCreatePatientItem(
                            encounter,
                            request,
                            requestedItem,
                            insurance,
                            service
                    );

            Optional<BillingChargeLine> existingChargeLine =
                    findActiveChargeLine(item.getId());

            BillingOperationResult billingResult;

            if (existingChargeLine.isPresent()) {
                BillingChargeLine chargeLine =
                        existingChargeLine.get();

                if (shouldRepriceExistingChargeLine(chargeLine)) {
                    LOG.info(
                            "[PREPARE_DEFAULT_SERVICES] Repricing existing setup-fallback "
                                    + "charge line pspId={} chargeLineId={} encounterId={}",
                            item.getId(),
                            chargeLine.getId(),
                            encounterId
                    );

                    billingResult =
                            billingEngineService.reprice(
                                    item.getId(),
                                    request.facilityId(),
                                    request.requestId().trim()
                                            + ":REPRICE:"
                                            + requestedItem.sequence()
                                            + ":"
                                            + requestedItem.serviceId()
                            );
                } else {
                    LOG.info(
                            "[PREPARE_DEFAULT_SERVICES] Skipping duplicate billing "
                                    + "pspId={} chargeLineId={} encounterId={}",
                            item.getId(),
                            chargeLine.getId(),
                            encounterId
                    );

                    billingResult =
                            toExistingBillingResult(
                                    item,
                                    chargeLine
                            );
                }
            } else {
                String itemRequestId =
                        request.requestId().trim()
                                + ":DEFAULT_SERVICE:"
                                + requestedItem.sequence()
                                + ":"
                                + requestedItem.serviceId();

                billingResult =
                        billingEngineService.processWithEventFallback(
                                item.getId(),
                                encounterId,
                                request.facilityId(),
                                itemRequestId
                        );
            }

            results.add(
                    new PrepareDefaultServicesResult.PreparedDefaultServiceResult(
                            item.getId(),
                            requestedItem.serviceId(),
                            requestedItem.sequence(),
                            billingResult
                    )
            );
        }

        boolean processed =
                results.stream()
                        .allMatch(result ->
                                result.billingResult() != null
                                        && result.billingResult().processed()
                        );

        LOG.info(
                "[PREPARE_DEFAULT_SERVICES] encounterId={} patientId={} coverageType={} itemCount={} processed={}",
                encounterId,
                request.patientId(),
                request.coverageType(),
                results.size(),
                processed
        );

        if (request.coverageType() == BillingCoverageType.INSURANCE) {
            encounterPreAuthorizationSyncService.scheduleSyncAfterCommit(
                    encounterId,
                    request.coverageType()
            );
        }

        return new PrepareDefaultServicesResult(
                request.patientId(),
                encounterId,
                request.facilityId(),
                request.coverageType(),
                insurance == null ? null : insurance.getId(),
                List.copyOf(results),
                processed,
                processed
                        ? "Default services prepared successfully."
                        : "Default services were prepared, but one or more billing rules could not be matched."
        );
    }

    private Optional<BillingChargeLine> findActiveChargeLine(
            Long patientServiceProductId
    ) {
        return billingChargeLineRepository
                .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                        patientServiceProductId,
                        EXCLUDED_CHARGE_LINE_STATUSES
                );
    }

    private boolean shouldRepriceExistingChargeLine(
            BillingChargeLine chargeLine
    ) {
        if (chargeLine.getAllocatedAmount() != null
                && chargeLine.getAllocatedAmount().signum() > 0) {
            return false;
        }

        return billingPricingSnapshotRepository
                .findTopByChargeLine_IdAndStatusOrderByIdDesc(
                        chargeLine.getId(),
                        BillingPricingSnapshotStatus.ACTIVE
                )
                .map(BillingPricingSnapshot::getPriceSource)
                .map(BillingPriceSource.SETUP_FALLBACK::equals)
                .orElse(false);
    }

    private BillingOperationResult toExistingBillingResult(
            PatientServiceAndProduct item,
            BillingChargeLine chargeLine
    ) {
        return new BillingOperationResult(
                item.getId(),
                chargeLine.getCharge().getId(),
                chargeLine.getId(),
                null,
                chargeLine.getGrossAmount(),
                chargeLine.getDiscountAmount(),
                chargeLine.getExemptionAmount(),
                chargeLine.getTaxAmount(),
                chargeLine.getNetAmount(),
                chargeLine.getPatientResponsibilityAmount(),
                chargeLine.getInsuranceResponsibilityAmount(),
                chargeLine.getReservedAmount(),
                true,
                "Service already calculated for this encounter."
        );
    }

    private PatientServiceAndProduct findOrCreatePatientItem(
            PatientEncounter encounter,
            PrepareDefaultServicesRequest request,
            PrepareDefaultServiceItem requestedItem,
            PatientInsurance insurance,
            ServiceSetupDTO service
    ) {
        PatientServiceAndProduct existing =
                patientServiceAndProductRepository
                        .findFirstByEncounterIdAndBillingItemTypeAndSourceIdAndIsDefaultServiceTrue(
                                encounter.getId(),
                                BillingItemTypes.SERVICE,
                                requestedItem.serviceId()
                        )
                        .orElse(null);

        if (existing != null) {
            validateExistingItem(existing, request, requestedItem, insurance);

            if (request.coverageType() == BillingCoverageType.INSURANCE) {
                PreAuthorizationResolutionService.Resolution preAuthorizationResolution =
                        preAuthorizationResolutionService.resolve(
                                encounter.getId(),
                                BillingItemTypes.SERVICE,
                                null,
                                requestedItem.serviceId(),
                                null,
                                null,
                                true
                        );
                preAuthorizationResolutionService.apply(existing, preAuthorizationResolution);
                existing = patientServiceAndProductRepository.saveAndFlush(existing);
            }

            return existing;
        }

        PatientServiceAndProduct.PatientServiceAndProductBuilder itemBuilder =
                PatientServiceAndProduct.builder()
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
                        .notes("Encounter default service: " + service.name());

        preAuthorizationResolutionService.resolveAndPrepareNewItem(
                itemBuilder,
                encounter.getId(),
                BillingItemTypes.SERVICE,
                null,
                requestedItem.serviceId(),
                null,
                null,
                request.coverageType() == BillingCoverageType.INSURANCE
        );

        PatientServiceAndProduct item = itemBuilder.build();

        return patientServiceAndProductRepository.saveAndFlush(item);
    }

    private void validateExistingItem(
            PatientServiceAndProduct existing,
            PrepareDefaultServicesRequest request,
            PrepareDefaultServiceItem requestedItem,
            PatientInsurance insurance
    ) {
        Long expectedInsuranceId =
                insurance == null ? null : insurance.getId();

        if (!request.patientId().equals(existing.getPatientId())) {
            throw new BadRequestAlertException(
                    "Existing default service belongs to another patient.",
                    ENTITY_NAME,
                    "existing.patient.mismatch"
            );
        }

        if (!request.currency().equals(existing.getCurrency())) {
            throw new BadRequestAlertException(
                    "Existing default service currency does not match facility currency.",
                    ENTITY_NAME,
                    "existing.currency.mismatch"
            );
        }

        if (!java.util.Objects.equals(
                expectedInsuranceId,
                existing.getPatientInsuranceId()
        )) {
            throw new BadRequestAlertException(
                    "Existing default service uses a different coverage selection. Reprice or cancel it before changing coverage.",
                    ENTITY_NAME,
                    "existing.coverage.mismatch"
            );
        }

        if (!requestedItem.quantity().equals(existing.getQuantity())) {
            throw new BadRequestAlertException(
                    "Existing default-service quantity differs from the request. Use repricing for quantity changes.",
                    ENTITY_NAME,
                    "existing.quantity.mismatch"
            );
        }
    }

    private PatientInsurance resolveInsurance(
            PrepareDefaultServicesRequest request
    ) {
        if (request.coverageType() == BillingCoverageType.SELF_PAY) {
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

        PatientInsurance insurance =
                patientInsuranceRepository
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

        requireText(
                insurance.getPayerNphiesId(),
                "Waseel payer NPHIES ID is required.",
                "payerNphiesId.required"
        );

        requireText(
                insurance.getMemberCardId(),
                "Waseel member-card ID is required.",
                "memberCardId.required"
        );

        return insurance;
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

            if (!Boolean.TRUE.equals(service.isActive())) {
                throw new BadRequestAlertException(
                        "Selected setup service is inactive.",
                        ENTITY_NAME,
                        "service.inactive"
                );
            }

            if (service.facilityId() != null
                    && !facilityId.equals(service.facilityId())) {
                throw new BadRequestAlertException(
                        "Selected setup service does not belong to the encounter facility.",
                        ENTITY_NAME,
                        "service.facility.mismatch"
                );
            }

            return service;

        } catch (FeignException.NotFound exception) {
            throw new NotFoundAlertException(
                    "Setup service not found with id " + serviceId,
                    ENTITY_NAME,
                    "service.notfound"
            );
        } catch (FeignException exception) {
            LOG.error(
                    "Setup Service call failed for serviceId={} status={} body={}",
                    serviceId,
                    exception.status(),
                    exception.contentUTF8(),
                    exception
            );
            throw new BadRequestAlertException(
                    "Unable to validate the selected service in Setup Service.",
                    ENTITY_NAME,
                    "setup.service.call.failed"
            );
        }
    }

    private Patient loadPatient(Long patientId) {
        return patientRepository.findById(patientId)
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
            PrepareDefaultServicesRequest request,
            Patient patient
    ) {
        PatientEncounter encounter =
                patientEncounterRepository.findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id " + encounterId,
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

    private void validateRequest(
            Long encounterId,
            PrepareDefaultServicesRequest request
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
                    "Prepare-default-services request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one default service must be selected.",
                    ENTITY_NAME,
                    "items.required"
            );
        }

        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new BadRequestAlertException(
                    "Request ID is required.",
                    ENTITY_NAME,
                    "requestId.required"
            );
        }

        Set<Long> serviceIds = new HashSet<>();
        Set<Integer> sequences = new HashSet<>();

        for (PrepareDefaultServiceItem item : request.items()) {
            if (item == null) {
                throw new BadRequestAlertException(
                        "Default-service item is required.",
                        ENTITY_NAME,
                        "item.required"
                );
            }

            if (!serviceIds.add(item.serviceId())) {
                throw new BadRequestAlertException(
                        "The same default service cannot be selected more than once.",
                        ENTITY_NAME,
                        "service.duplicate"
                );
            }

            if (!sequences.add(item.sequence())) {
                throw new BadRequestAlertException(
                        "Default-service sequence values must be unique.",
                        ENTITY_NAME,
                        "sequence.duplicate"
                );
            }
        }
    }

    private void requireText(
            String value,
            String message,
            String errorKey
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestAlertException(
                    message,
                    ENTITY_NAME,
                    errorKey
            );
        }
    }
}
