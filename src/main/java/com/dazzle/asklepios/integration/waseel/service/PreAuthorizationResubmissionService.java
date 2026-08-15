package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationTrackingResponse;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.PatientItemPricingApplicationService;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PreAuthorizationResubmissionService {

    private static final Logger LOG =
            LoggerFactory.getLogger(PreAuthorizationResubmissionService.class);

    static final String RESUBMITTED_FROM_PREFIX = "RESUBMITTED_FROM:";
    static final String RESUBMITTED_AS_PREFIX = "RESUBMITTED_AS:";

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientItemPricingApplicationService patientItemPricingApplicationService;
    private final EncounterPreAuthorizationSyncService encounterPreAuthorizationSyncService;
    private final PreAuthorizationTrackingService trackingService;

    @Transactional
    public PreAuthorizationTrackingResponse resubmit(Long preAuthorizationId) {
        PreAuthorizationRequest original =
                preAuthorizationRequestRepository.findById(preAuthorizationId)
                        .orElseThrow(() -> new NotFoundAlertException(
                                "Pre-authorization not found with id " + preAuthorizationId,
                                "preAuthorization",
                                "notfound"
                        ));

        validateResubmittable(original);

        List<PatientServiceAndProduct> items = resolveLinkedItems(original);
        if (items.isEmpty()) {
            throw new BadRequestAlertException(
                    "No billing items are linked to this pre-authorization to resubmit.",
                    "preAuthorization",
                    "resubmit.items.notFound"
            );
        }

        for (PatientServiceAndProduct item : items) {
            if (Boolean.TRUE.equals(item.getIsBilled())) {
                throw new BadRequestAlertException(
                        "Cannot resubmit because a linked item is already billed.",
                        "preAuthorization",
                        "resubmit.item.alreadyBilled"
                );
            }
            if (item.getPaymentStatus() == PaymentStatus.CANCELLED) {
                throw new BadRequestAlertException(
                        "Cannot resubmit because a linked item is cancelled.",
                        "preAuthorization",
                        "resubmit.item.cancelled"
                );
            }
        }

        PatientEncounter encounter = patientEncounterRepository.findById(original.getEncounterId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found.",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        for (PatientServiceAndProduct item : items) {
            patientItemPricingApplicationService.applyToItem(item, encounter.getFacilityId());
            item.setPreAuthorizationStatus(PreAuthorizationStatus.PENDING_APPROVAL);
            item.setPreAuthorizationRequired(true);
            item.setPaymentStatus(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
            item.setPreAuthorizationRequestId(null);
            item.setPreAuthorizationReferenceNo(null);
        }
        patientServiceAndProductRepository.saveAll(items);
        patientServiceAndProductRepository.flush();

        LOG.info(
                "[PREAUTH_RESUBMIT] Reset {} items from preAuthorizationId={} encounterId={}",
                items.size(),
                original.getId(),
                original.getEncounterId()
        );

        try {
            encounterPreAuthorizationSyncService.submitPendingPreAuthorizationOrThrow(
                    original.getEncounterId()
            );
        } catch (RuntimeException exception) {
            throw new BadRequestAlertException(
                    exception.getMessage() == null
                            ? "Failed to resubmit pre-authorization to Waseel."
                            : exception.getMessage(),
                    "preAuthorization",
                    "resubmit.submit.failed"
            );
        }

        PreAuthorizationRequest resubmitted =
                preAuthorizationRequestRepository
                        .findByEncounterIdOrderByIdDesc(original.getEncounterId())
                        .stream()
                        .filter(request -> !Objects.equals(request.getId(), original.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BadRequestAlertException(
                                "Resubmitted pre-authorization was not created.",
                                "preAuthorization",
                                "resubmit.submit.failed"
                        ));

        original.setStatusReason(RESUBMITTED_AS_PREFIX + resubmitted.getId());
        preAuthorizationRequestRepository.save(original);

        resubmitted.setStatusReason(RESUBMITTED_FROM_PREFIX + original.getId());
        preAuthorizationRequestRepository.save(resubmitted);

        return trackingService.findById(resubmitted.getId());
    }

    static boolean isResubmittable(PreAuthorizationRequest request) {
        if (request == null || Boolean.TRUE.equals(request.getIsCancelled())) {
            return false;
        }

        String status = normalize(request.getStatus());
        String outcome = normalize(request.getOutcome());
        String message = normalize(request.getMessage());

        if ("failed".equals(status)
                || "error".equals(status)
                || "rejected".equals(status)
                || "denied".equals(status)) {
            return true;
        }

        return outcome.contains("error")
                || outcome.contains("reject")
                || outcome.contains("denied")
                || message.contains("error")
                || message.contains("bv-");
    }

    static Long parseResubmittedFromId(String statusReason) {
        return parsePrefixedId(statusReason, RESUBMITTED_FROM_PREFIX);
    }

    static Long parseResubmittedAsId(String statusReason) {
        return parsePrefixedId(statusReason, RESUBMITTED_AS_PREFIX);
    }

    private void validateResubmittable(PreAuthorizationRequest original) {
        if (Boolean.TRUE.equals(original.getIsCancelled())) {
            throw new BadRequestAlertException(
                    "Cancelled pre-authorizations cannot be resubmitted.",
                    "preAuthorization",
                    "resubmit.cancelled"
            );
        }

        if (!isResubmittable(original)) {
            throw new BadRequestAlertException(
                    "Only failed or rejected pre-authorizations can be resubmitted.",
                    "preAuthorization",
                    "resubmit.notEligible"
            );
        }
    }

    private List<PatientServiceAndProduct> resolveLinkedItems(PreAuthorizationRequest original) {
        List<PatientServiceAndProduct> linked =
                patientServiceAndProductRepository.findByPreAuthorizationRequestId(original.getId());
        if (!linked.isEmpty()) {
            return linked;
        }

        List<PreAuthorizationItem> preAuthItems =
                preAuthorizationItemRepository.findByPreAuthorizationIdOrderBySequenceAsc(original.getId());
        if (preAuthItems.isEmpty()) {
            return List.of();
        }

        List<PatientServiceAndProduct> encounterItems =
                patientServiceAndProductRepository.findByEncounterId(original.getEncounterId());
        List<PatientServiceAndProduct> matched = new ArrayList<>();

        for (PreAuthorizationItem preAuthItem : preAuthItems) {
            encounterItems.stream()
                    .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                    .filter(item -> matchesCatalogItem(item, preAuthItem))
                    .findFirst()
                    .ifPresent(matched::add);
        }

        return matched;
    }

    private boolean matchesCatalogItem(
            PatientServiceAndProduct item,
            PreAuthorizationItem preAuthItem
    ) {
        if (preAuthItem.getProcedureId() != null) {
            return Objects.equals(item.getProcedureId(), preAuthItem.getProcedureId());
        }
        if (preAuthItem.getServiceId() != null) {
            return Objects.equals(item.getServiceId(), preAuthItem.getServiceId());
        }
        if (preAuthItem.getDiagnosticTestId() != null) {
            return Objects.equals(item.getDiagnosticTestId(), preAuthItem.getDiagnosticTestId());
        }
        if (preAuthItem.getBrandMedicationId() != null) {
            return Objects.equals(item.getBrandMedicationId(), preAuthItem.getBrandMedicationId());
        }
        return false;
    }

    private static Long parsePrefixedId(String statusReason, String prefix) {
        if (statusReason == null || !statusReason.startsWith(prefix)) {
            return null;
        }

        try {
            return Long.valueOf(statusReason.substring(prefix.length()).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
