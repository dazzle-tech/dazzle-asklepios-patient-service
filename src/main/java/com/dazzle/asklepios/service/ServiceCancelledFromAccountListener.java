package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.event.ServiceCancelledFromAccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Reflects a service cancelled from the patient account on the visit: an order with no billing line
 * left is not going to be done, so it is cancelled with the same reason. Runs in the cancel's transaction.
 * <ul>
 *   <li>A dental procedure bills one line per tooth: it is cancelled once every tooth is cancelled.
 *       Until then the screen shows each tooth's state.</li>
 *   <li>A procedure is cancelled with its line, unless it was already performed.</li>
 * </ul>
 */
@Service
public class ServiceCancelledFromAccountListener {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceCancelledFromAccountListener.class);

    private static final Set<ProcStatus> PROCEDURE_FINAL_STATUSES = Set.of(ProcStatus.CANCELLED, ProcStatus.COMPLETED);

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final DentalProcedureRepository dentalProcedureRepository;
    private final PatientProcedureRepository patientProcedureRepository;
    private final DentalProcedureService dentalProcedureService;
    private final PatientProcedureService patientProcedureService;

    public ServiceCancelledFromAccountListener(
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            DentalProcedureRepository dentalProcedureRepository,
            PatientProcedureRepository patientProcedureRepository,
            @Lazy DentalProcedureService dentalProcedureService,
            @Lazy PatientProcedureService patientProcedureService
    ) {
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.dentalProcedureRepository = dentalProcedureRepository;
        this.patientProcedureRepository = patientProcedureRepository;
        this.dentalProcedureService = dentalProcedureService;
        this.patientProcedureService = patientProcedureService;
    }

    @EventListener
    public void onServiceCancelled(ServiceCancelledFromAccountEvent event) {
        if (event.sourceId() == null || event.billingItemType() != BillingItemTypes.PROCEDURE) {
            return;
        }
        if (event.serviceSource() == ServiceSource.DENTAL_PROCEDURE) {
            cancelDentalProcedureWhenNoToothLeft(event);
        } else if (event.serviceSource() == ServiceSource.PROCEDURE) {
            cancelProcedure(event);
        }
    }

    private void cancelDentalProcedureWhenNoToothLeft(ServiceCancelledFromAccountEvent event) {
        List<PatientServiceAndProduct> toothLines = patientServiceAndProductRepository
                .findAllByServiceSourceAndSourceIdAndBillingItemTypeOrderByIdAsc(
                        ServiceSource.DENTAL_PROCEDURE, event.sourceId(), BillingItemTypes.PROCEDURE);
        if (!allCancelled(toothLines)) {
            return;
        }
        dentalProcedureRepository.findById(event.sourceId())
                .filter(procedure -> !procedure.isCancelled())
                .ifPresent(procedure -> {
                    LOG.info("[ACCOUNT_CANCEL] Every tooth cancelled — cancelling dental procedure id={}", procedure.getId());
                    dentalProcedureService.cancel(procedure.getId(), event.reason());
                });
    }

    private void cancelProcedure(ServiceCancelledFromAccountEvent event) {
        patientProcedureRepository.findById(event.sourceId())
                .filter(procedure -> !PROCEDURE_FINAL_STATUSES.contains(procedure.getStatus()))
                .ifPresent(procedure -> {
                    LOG.info("[ACCOUNT_CANCEL] Line cancelled — cancelling procedure id={}", procedure.getId());
                    patientProcedureService.cancel(procedure.getId(), event.reason());
                });
    }

    static boolean allCancelled(List<PatientServiceAndProduct> lines) {
        return !lines.isEmpty() && lines.stream().allMatch(line -> line.getPaymentStatus() == PaymentStatus.CANCELLED);
    }
}
