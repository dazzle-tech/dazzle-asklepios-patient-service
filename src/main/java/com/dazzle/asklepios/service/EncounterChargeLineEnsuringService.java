package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;

/**
 * Ensures every billable encounter service has an active billing charge line
 * before checkout or invoice generation.
 */
@Service
public class EncounterChargeLineEnsuringService {

    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterChargeLineEnsuringService.class);

    private static final String ENTITY_NAME = "encounterChargeLineEnsuring";

    private static final int MONEY_SCALE = 4;

    private static final EnumSet<PaymentStatus> EXCLUDED_PAYMENT_STATUSES =
            EnumSet.of(
                    PaymentStatus.CANCELLED,
                    PaymentStatus.EXEMPTED,
                    PaymentStatus.SKIPPED_PENDING_PRE_AUTH
            );

    private final PatientEncounterRepository patientEncounterRepository;

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    private final BillingChargeService billingChargeService;

    private final BillingEngineService billingEngineService;

    private final EncounterChargeLineEnsuringService self;

    public EncounterChargeLineEnsuringService(
            PatientEncounterRepository patientEncounterRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            BillingChargeService billingChargeService,
            @Lazy BillingEngineService billingEngineService,
            @Lazy EncounterChargeLineEnsuringService self
    ) {
        this.patientEncounterRepository = patientEncounterRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.billingChargeService = billingChargeService;
        this.billingEngineService = billingEngineService;
        this.self = self;
    }

    public void ensureEncounterChargeLines(
            Long encounterId,
            String requestIdPrefix
    ) {
        if (encounterId == null) {
            return;
        }

        if (!patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                    "Encounter not found with id " + encounterId,
                    ENTITY_NAME,
                    "encounter.notfound"
            );
        }

        List<PatientServiceAndProduct> encounterServices =
                patientServiceAndProductRepository.findByEncounterId(
                        encounterId
                );

        String baseRequestId =
                requestIdPrefix == null || requestIdPrefix.isBlank()
                        ? "ENSURE_ENCOUNTER_CHARGE:" + encounterId
                        : requestIdPrefix.trim();

        int createdCount = 0;

        for (PatientServiceAndProduct service : encounterServices) {
            if (!isAutoBillable(service)) {
                continue;
            }

            if (billingChargeService
                    .findActiveChargeLine(service.getId(), encounterId)
                    .isPresent()) {
                continue;
            }

            LOG.info(
                    "[ENSURE_ENCOUNTER_CHARGE] Creating missing charge line "
                            + "encounterId={} pspId={} requestId={}",
                    encounterId,
                    service.getId(),
                    baseRequestId
            );

            self.ensureChargeLineInNewTransaction(
                    service.getId(),
                    encounterId,
                    baseRequestId
            );

            createdCount++;
        }

        if (createdCount > 0) {
            LOG.info(
                    "[ENSURE_ENCOUNTER_CHARGE] Created {} charge line(s) for encounter {}",
                    createdCount,
                    encounterId
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void ensureChargeLineInNewTransaction(
            Long patientServiceProductId,
            Long encounterId,
            String baseRequestId
    ) {
        PatientServiceAndProduct service =
                patientServiceAndProductRepository
                        .findById(patientServiceProductId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Patient service/product not found with id "
                                                + patientServiceProductId,
                                        ENTITY_NAME,
                                        "patientServiceProduct.notfound"
                                )
                        );

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

        if (!isAutoBillable(service)) {
            return;
        }

        if (billingChargeService
                .findActiveChargeLine(service.getId(), encounterId)
                .isPresent()) {
            return;
        }

        String requestId = baseRequestId + ":PSP:" + service.getId();

        if (shouldUseEncounterPricingFallback(service)) {
            createChargeLineFromEncounterPricing(
                    service,
                    requestId,
                    "Encounter-priced item"
            );
            return;
        }

        try {
            billingEngineService.ensureChargeLineCreated(
                    service.getId(),
                    encounter.getId(),
                    encounter.getFacilityId(),
                    requestId
            );
        } catch (NotFoundAlertException exception) {
            createChargeLineFromEncounterPricing(
                    service,
                    requestId,
                    exception.getMessage()
            );
        } catch (BadRequestAlertException exception) {
            if ("chargeLine.create.failed".equals(resolveErrorKey(exception))) {
                createChargeLineFromEncounterPricing(
                        service,
                        requestId,
                        exception.getMessage()
                );
                return;
            }

            throw exception;
        } catch (RuntimeException exception) {
            if (hasEncounterPricing(service)) {
                createChargeLineFromEncounterPricing(
                        service,
                        requestId,
                        exception.getMessage()
                );
                return;
            }

            throw exception;
        }
    }

    private boolean shouldUseEncounterPricingFallback(
            PatientServiceAndProduct service
    ) {
        return hasEncounterPricing(service);
    }

    private void createChargeLineFromEncounterPricing(
            PatientServiceAndProduct service,
            String requestId,
            String reason
    ) {
        if (!hasEncounterPricing(service)) {
            throw new BadRequestAlertException(
                    "Unable to create a billing charge line for patient service/product "
                            + service.getId()
                            + ". Bill the item from Prepare Services first.",
                    ENTITY_NAME,
                    "chargeLine.create.failed"
            );
        }

        LOG.warn(
                "[ENSURE_ENCOUNTER_CHARGE] Using encounter pricing for pspId={}. reason={}",
                service.getId(),
                reason
        );

        long quantity =
                service.getQuantity() == null || service.getQuantity() <= 0
                        ? 1L
                        : service.getQuantity();

        BigDecimal patientShare = money(service.getPatientShareAmount());
        if (patientShare.signum() <= 0) {
            patientShare = money(service.getNetAmount());
        }
        if (patientShare.signum() <= 0) {
            patientShare = money(service.getTotalAmount());
        }
        if (patientShare.signum() <= 0) {
            patientShare = money(service.getRemainingAmount());
        }

        BigDecimal unitPrice = money(service.getUnitPrice());
        if (unitPrice.signum() <= 0 && money(service.getNetAmount()).signum() > 0) {
            unitPrice =
                    money(service.getNetAmount())
                            .divide(
                                    BigDecimal.valueOf(quantity),
                                    MONEY_SCALE,
                                    RoundingMode.HALF_UP
                            );
        }
        if (unitPrice.signum() <= 0 && money(service.getTotalAmount()).signum() > 0) {
            unitPrice =
                    money(service.getTotalAmount())
                            .divide(
                                    BigDecimal.valueOf(quantity),
                                    MONEY_SCALE,
                                    RoundingMode.HALF_UP
                            );
        }
        if (unitPrice.signum() <= 0 && patientShare.signum() > 0) {
            unitPrice =
                    patientShare
                            .divide(
                                    BigDecimal.valueOf(quantity),
                                    MONEY_SCALE,
                                    RoundingMode.HALF_UP
                            );
        }
        if (patientShare.signum() <= 0 && unitPrice.signum() > 0) {
            patientShare =
                    unitPrice
                            .multiply(BigDecimal.valueOf(quantity))
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }

        billingChargeService.createDebitNoteAdjustmentChargeLine(
                service,
                BigDecimal.valueOf(quantity),
                unitPrice,
                patientShare,
                money(service.getInsuranceShareAmount()),
                requestId
        );
    }

    private boolean hasEncounterPricing(PatientServiceAndProduct service) {
        return resolveCollectableAmount(service).signum() > 0;
    }

    private String resolveErrorKey(BadRequestAlertException exception) {
        if (exception.getBody() == null) {
            return null;
        }

        Object errorKey = exception.getBody().getProperties().get("errorKey");
        return errorKey == null ? null : String.valueOf(errorKey);
    }

    private boolean isAutoBillable(PatientServiceAndProduct service) {
        if (service == null || service.getId() == null) {
            return false;
        }

        if (EXCLUDED_PAYMENT_STATUSES.contains(service.getPaymentStatus())) {
            return false;
        }

        if (Boolean.TRUE.equals(service.getIsExempted())
                && money(service.getPatientShareAmount()).signum() == 0
                && money(service.getNetAmount()).signum() == 0) {
            return false;
        }

        return resolveCollectableAmount(service).signum() > 0;
    }

    private BigDecimal resolveCollectableAmount(
            PatientServiceAndProduct service
    ) {
        BigDecimal remaining = money(service.getRemainingAmount());
        if (remaining.signum() > 0) {
            return remaining;
        }

        BigDecimal patientShare = money(service.getPatientShareAmount());
        if (patientShare.signum() > 0) {
            return patientShare;
        }

        BigDecimal netAmount = money(service.getNetAmount());
        if (netAmount.signum() > 0) {
            return netAmount;
        }

        BigDecimal totalAmount = money(service.getTotalAmount());
        if (totalAmount.signum() > 0) {
            return totalAmount;
        }

        long quantity =
                service.getQuantity() == null || service.getQuantity() <= 0
                        ? 1L
                        : service.getQuantity();

        return money(service.getUnitPrice())
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
