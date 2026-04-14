package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientUccMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.repository.PatientUccMedicationOrderRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class PatientUccMedicationOrderStatusService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientUccMedicationOrderStatusService.class);

    private final PatientUccMedicationOrderRepository patientUccMedicationOrderRepository;

    public PatientUccMedicationOrderStatusService(PatientUccMedicationOrderRepository patientUccMedicationOrderRepository) {
        this.patientUccMedicationOrderRepository = patientUccMedicationOrderRepository;
    }

    public PatientUccMedicationOrder submit(Long orderId, String username, Boolean isHighAlert) {
        LOG.debug("[STATUS][SUBMIT] request -> orderId={} username={} isHighAlert={}", orderId, username, isHighAlert);

        PatientUccMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.SUBMITTED);

        order.setIsHighAlert(isHighAlert);
        order.setStatus(MedicationOrderStatus.SUBMITTED);
        order.setSubmittedBy(username);
        order.setSubmittedDate(Instant.now());

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][SUBMIT] saved -> id={} status={} submittedBy={}",
                saved.getId(), saved.getStatus(), saved.getSubmittedBy());

        return saved;
    }

    public PatientUccMedicationOrder administer(Long orderId, String username) {
        LOG.debug("[STATUS][ADMINISTER] request -> orderId={} username={}", orderId, username);

        PatientUccMedicationOrder order = getOrder(orderId);

        if (order.getStatus() != MedicationOrderStatus.SUBMITTED) {
            LOG.error("[STATUS][ADMINISTER] invalid status -> currentStatus={}", order.getStatus());
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "patient_ucc_medication_order",
                    "Administer is allowed only when status is SUBMITTED"
            );
        }

        if (Boolean.TRUE.equals(order.getIsHighAlert())) {
            order.setStatus(MedicationOrderStatus.WAITING_DOUBLE_CHECK);
            LOG.debug("[STATUS][ADMINISTER] high alert order -> move to WAITING_DOUBLE_CHECK");
        } else {
            order.setStatus(MedicationOrderStatus.ADMINISTERED);
            LOG.debug("[STATUS][ADMINISTER] normal order -> move to ADMINISTERED");
        }

        order.setAdministeredBy(username);
        order.setAdministeredDate(Instant.now());

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][ADMINISTER] saved -> id={} status={} administeredBy={}",
                saved.getId(), saved.getStatus(), saved.getAdministeredBy());

        return saved;
    }

    public PatientUccMedicationOrder doubleCheck(Long orderId, String username) {
        LOG.debug("[STATUS][DOUBLE_CHECK] request -> orderId={} username={}", orderId, username);

        PatientUccMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.ADMINISTERED);

        if (order.getStatus() != MedicationOrderStatus.WAITING_DOUBLE_CHECK) {
            LOG.error("[STATUS][DOUBLE_CHECK] invalid status -> currentStatus={}", order.getStatus());
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "patient_ucc_medication_order",
                    "Double-check is allowed only when status is WAITING_DOUBLE_CHECK"
            );
        }

        order.setStatus(MedicationOrderStatus.ADMINISTERED);
        order.setDoubleCheckedBy(username);
        order.setDoubleCheckedDate(Instant.now());
        order.setAdministeredBy(username);
        order.setAdministeredDate(Instant.now());

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][DOUBLE_CHECK] saved -> id={} status={} doubleCheckedBy={}",
                saved.getId(), saved.getStatus(), saved.getDoubleCheckedBy());

        return saved;
    }

    public PatientUccMedicationOrder discard(Long orderId, String username, String discardReason) {
        LOG.debug("[STATUS][DISCARD] request -> orderId={} username={} reason={}", orderId, username, discardReason);

        PatientUccMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.DISCARDED);

        order.setStatus(MedicationOrderStatus.DISCARDED);
        order.setDiscardedBy(username);
        order.setDiscardedDate(Instant.now());
        order.setDiscardReason(discardReason);

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][DISCARD] saved -> id={} status={} discardedBy={}",
                saved.getId(), saved.getStatus(), saved.getDiscardedBy());

        return saved;
    }

    public PatientUccMedicationOrder cancel(Long orderId, String username, String cancellationReason) {
        LOG.debug("[STATUS][CANCEL] request -> orderId={} username={} reason={}", orderId, username, cancellationReason);

        PatientUccMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.CANCELLED);

        order.setStatus(MedicationOrderStatus.CANCELLED);
        order.setCancelledBy(username);
        order.setCancelledDate(Instant.now());
        order.setCancellationReason(cancellationReason);

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][CANCEL] saved -> id={} status={} cancelledBy={}",
                saved.getId(), saved.getStatus(), saved.getCancelledBy());

        return saved;
    }

    private PatientUccMedicationOrder getOrder(Long orderId) {
        LOG.debug("[STATUS][GET_ORDER] request -> orderId={}", orderId);

        PatientUccMedicationOrder order = patientUccMedicationOrderRepository.findById(orderId)
                .orElseThrow(() -> {
                    LOG.error("[STATUS][GET_ORDER] not found -> orderId={}", orderId);
                    return new BadRequestAlertException(
                            "notfound",
                            "patient_ucc_medication_order",
                            "PatientUccMedicationOrder not found with id " + orderId
                    );
                });

        LOG.debug("[STATUS][GET_ORDER] found -> id={} status={}", order.getId(), order.getStatus());
        return order;
    }

    private void ensureTransition(PatientUccMedicationOrder order, MedicationOrderStatus to) {
        MedicationOrderStatus from = order.getStatus() == null ? MedicationOrderStatus.NEW : order.getStatus();

        LOG.debug("[STATUS][TRANSITION] validate -> from={} to={} orderId={}",
                from, to, order.getId());

        if (to == MedicationOrderStatus.SUBMITTED) {
            if (from != MedicationOrderStatus.NEW) {
                LOG.error("[STATUS][TRANSITION] invalid -> {} -> {}", from, to);
                throw invalid(from, to);
            }
            return;
        }

        if (to == MedicationOrderStatus.CANCELLED) {
            if (from == MedicationOrderStatus.CANCELLED ||
                    from == MedicationOrderStatus.ADMINISTERED ||
                    from == MedicationOrderStatus.DISCARDED) {
                LOG.error("[STATUS][TRANSITION] invalid -> {} -> {}", from, to);
                throw invalid(from, to);
            }
            return;
        }

        if (to == MedicationOrderStatus.DISCARDED) {
            if (from == MedicationOrderStatus.ADMINISTERED ||
                    from == MedicationOrderStatus.DISCARDED ||
                    from == MedicationOrderStatus.CANCELLED) {
                LOG.error("[STATUS][TRANSITION] invalid -> {} -> {}", from, to);
                throw invalid(from, to);
            }
            return;
        }

        if (to == MedicationOrderStatus.ADMINISTERED) {
            if (from != MedicationOrderStatus.WAITING_DOUBLE_CHECK) {
                LOG.error("[STATUS][TRANSITION] invalid -> {} -> {}", from, to);
                throw invalid(from, to);
            }
        }
    }

    private BadRequestAlertException invalid(MedicationOrderStatus from, MedicationOrderStatus to) {
        LOG.error("[STATUS][INVALID_TRANSITION] {} -> {}", from, to);
        return new BadRequestAlertException(
                "invalid_transition",
                "patient_ucc_medication_order",
                "Invalid transition " + from + " -> " + to
        );
    }
}