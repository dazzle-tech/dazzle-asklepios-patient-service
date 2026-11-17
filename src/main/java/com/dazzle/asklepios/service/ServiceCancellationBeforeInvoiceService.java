package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.EncounterBillingStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.billing.EncounterHasInvoiceResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Lets a user holding the "cancel service before invoice" access cancel any
 * encounter service, even one already performed, paid or on a closed visit,
 * as long as no invoice exists for the encounter.
 *
 * The cancellation is financial only: the service's money goes back to the
 * patient wallet, its pre-authorization is cancelled through the existing
 * flow, and for insurance visits the max-limit pool is spread again over the
 * remaining services.
 */
@Service
@RequiredArgsConstructor
public class ServiceCancellationBeforeInvoiceService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    ServiceCancellationBeforeInvoiceService.class
            );

    private static final String ENTITY_NAME = "serviceCancellationBeforeInvoice";

    private final UserRepository userRepository;

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    private final PatientEncounterRepository patientEncounterRepository;

    private final InvoiceGenerationService invoiceGenerationService;

    private final PatientServiceAndProductService patientServiceAndProductService;

    private final BillingInsuranceShareRedistributionService billingInsuranceShareRedistributionService;

    @Transactional(rollbackFor = Exception.class)
    public void cancel(
            Long patientServiceProductId,
            String reason
    ) {
        String login = requireAuthorizedUser();

        PatientServiceAndProduct item =
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

        if (item.getPaymentStatus() == PaymentStatus.CANCELLED) {
            return;
        }

        rejectIfInvoiced(item.getEncounterId());

        Long poolInsuranceId =
                sharesVisitPool(item)
                        ? item.getPatientInsuranceId()
                        : null;

        String cancelReason = reason.trim();

        patientServiceAndProductService.cancel(
                item.getId(),
                cancelReason
        );

        if (poolInsuranceId != null) {
            billingInsuranceShareRedistributionService
                    .redistributeVisitMaxLimit(
                            item.getEncounterId(),
                            poolInsuranceId,
                            "Visit max limit redistributed after cancelling service "
                                    + item.getId()
                                    + ". "
                                    + cancelReason,
                            login,
                            "cancel-before-invoice-"
                                    + item.getId()
                                    + "-"
                                    + Instant.now().toEpochMilli()
                                    + "-"
                                    + UUID.randomUUID()
                    );
        }

        LOG.info(
                "[CANCEL_BEFORE_INVOICE] Service cancelled pspId={} "
                        + "encounterId={} by={} redistributedInsuranceId={}",
                item.getId(),
                item.getEncounterId(),
                login,
                poolInsuranceId
        );
    }

    private String requireAuthorizedUser() {
        String login =
                SecurityUtils.getCurrentUserLogin()
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Current user is not authenticated.",
                                        ENTITY_NAME,
                                        "user.notAuthenticated"
                                )
                        );

        boolean allowed =
                userRepository
                        .findByLogin(login)
                        .map(User::isCanCancelServiceBeforeInvoice)
                        .orElse(false);

        if (!allowed) {
            throw new BadRequestAlertException(
                    "You do not have permission to cancel services before the invoice is created.",
                    ENTITY_NAME,
                    "permissionDenied"
            );
        }

        return login;
    }

    private void rejectIfInvoiced(Long encounterId) {
        PatientEncounter encounter =
                patientEncounterRepository
                        .findById(encounterId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Encounter not found with id " + encounterId,
                                        ENTITY_NAME,
                                        "encounter.notfound"
                                )
                        );

        EncounterHasInvoiceResponse invoiceResponse =
                invoiceGenerationService.hasInvoice(encounterId);

        if (encounter.getBillingStatus() == EncounterBillingStatus.INVOICED
                || (invoiceResponse != null && invoiceResponse.hasInvoice())) {
            throw new BadRequestAlertException(
                    "Cannot cancel the service because an invoice has already been generated.",
                    ENTITY_NAME,
                    "invoiceExists"
            );
        }
    }

    /**
     * Only an insurance-covered patient share draws from the visit max limit.
     */
    private boolean sharesVisitPool(PatientServiceAndProduct item) {
        return item.getPatientInsuranceId() != null
                && !Boolean.TRUE.equals(item.getIsExempted())
                && !item.isUncoveredCashItem();
    }
}
