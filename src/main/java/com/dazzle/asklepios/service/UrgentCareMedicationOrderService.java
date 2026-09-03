package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.UrgentCareMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.UrgentCareMedicationOrderRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.urgentcaremedicationorders.UrgentCareMedicationOrderUpdateDTO;
import com.dazzle.asklepios.service.helper.ActiveIngredientHelper;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class UrgentCareMedicationOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(UrgentCareMedicationOrderService.class);

    private final UrgentCareMedicationOrderRepository urgentCareMedicationOrderRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ActiveIngredientHelper activeIngredientHelper;
    private final DepartmentHelper departmentHelper;
    private final FacilityHelper facilityHelper;
    private final NotificationHelper notificationHelper;

    public UrgentCareMedicationOrderService(
            UrgentCareMedicationOrderRepository urgentCareMedicationOrderRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository patientEncounterRepository, ActiveIngredientHelper activeIngredientHelper, DepartmentHelper departmentHelper, FacilityHelper facilityHelper, NotificationHelper notificationHelper) {
        this.urgentCareMedicationOrderRepository = urgentCareMedicationOrderRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.activeIngredientHelper = activeIngredientHelper;
        this.departmentHelper = departmentHelper;
        this.facilityHelper = facilityHelper;
        this.notificationHelper = notificationHelper;
    }

    public UrgentCareMedicationOrder create(UrgentCareMedicationOrderCreateDTO dto) {
        LOG.debug("Request to create PatientUccMedicationOrder: {}", dto);
        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Patient not found with id " + dto.patientId(),
                                "UrgentCareMedicationOrder",
                                "patient.notfound"
                        )
                );
        PatientEncounter encounter = patientEncounterRepository.findById(dto.encounterId())
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Encounter not found with id " + dto.encounterId(),
                                "UrgentCareMedicationOrder",
                                "encounter.notfound"
                        )
                );
        activeIngredientHelper.validateActiveIngredientExists(dto.activeIngredientId());
        UrgentCareMedicationOrder order = new UrgentCareMedicationOrder();
        order.setActiveIngredientId(dto.activeIngredientId());
        order.setInstructionType(dto.instructionType());
        order.setInstructionText(dto.instructionText());
        order.setDose(dto.dose());
        order.setDoseUnit(dto.doseUnit());
        order.setRoute(dto.route());
        order.setFrequency(dto.frequency());

        order.setStatus(MedicationOrderStatus.NEW);
        order.setPatient(patient);
        order.setEncounter(encounter);

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.saveAndFlush(order);

        LOG.debug("[SERVICE][CREATE] saved -> id={} patientId={} encounterId={} status={}",
                saved.getId(),
                dto.patientId(),
                dto.encounterId(),
                saved.getStatus());

        notificationForUCCMedicationOrderCreated(saved, patient, encounter);

        return saved;
    }

    public UrgentCareMedicationOrder update(UrgentCareMedicationOrder existing, UrgentCareMedicationOrderUpdateDTO dto) {
        LOG.debug("[SERVICE][UPDATE] request -> existingId={} payload={}", existing.getId(), dto);
        activeIngredientHelper.validateActiveIngredientExists(dto.activeIngredientId());
        existing.setActiveIngredientId(dto.activeIngredientId());
        existing.setInstructionType(dto.instructionType());
        existing.setInstructionText(dto.instructionText());
        existing.setDose(dto.dose());
        existing.setDoseUnit(dto.doseUnit());
        existing.setRoute(dto.route());
        existing.setFrequency(dto.frequency());

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.save(existing);

        LOG.debug("[SERVICE][UPDATE] saved -> id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public UrgentCareMedicationOrder findOne(Long id) {
        LOG.debug("[SERVICE][FIND_ONE] request -> id={}", id);

        UrgentCareMedicationOrder order = urgentCareMedicationOrderRepository.findById(id)
                .orElseThrow(() -> {
                    LOG.error("[SERVICE][FIND_ONE] not found -> id={}", id);
                    return new BadRequestAlertException(
                            "notfound",
                            "patient_ucc_medication_order",
                            "PatientUccMedicationOrder not found with id " + id
                    );
                });

        LOG.debug("[SERVICE][FIND_ONE] found -> id={} status={}", order.getId(), order.getStatus());
        return order;
    }

    @Transactional(readOnly = true)
    public Page<UrgentCareMedicationOrder> findAll(Pageable pageable) {
        LOG.debug("[SERVICE][FIND_ALL] request -> pageable={}", pageable);

        Page<UrgentCareMedicationOrder> page = urgentCareMedicationOrderRepository.findAll(pageable);

        LOG.debug("[SERVICE][FIND_ALL] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }

    @Transactional(readOnly = true)
    public Page<UrgentCareMedicationOrder> findByStatus(MedicationOrderStatus status, Pageable pageable) {
        LOG.debug("[SERVICE][FIND_BY_STATUS] request -> status={} pageable={}", status, pageable);

        Page<UrgentCareMedicationOrder> page = urgentCareMedicationOrderRepository.findByStatus(status, pageable);

        LOG.debug("[SERVICE][FIND_BY_STATUS] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }

    @Transactional(readOnly = true)
    public Page<UrgentCareMedicationOrder> filter(Specification<UrgentCareMedicationOrder> spec, Pageable pageable) {
        LOG.debug("[SERVICE][FILTER] request -> pageable={}", pageable);

        Page<UrgentCareMedicationOrder> page = urgentCareMedicationOrderRepository.findAll(spec, pageable);

        LOG.debug("[SERVICE][FILTER] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }


    public UrgentCareMedicationOrder submit(Long orderId, String username, Boolean isHighAlert) {
        LOG.debug("[STATUS][SUBMIT] request -> orderId={} username={} isHighAlert={}", orderId, username, isHighAlert);

        UrgentCareMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.SUBMITTED);

        order.setIsHighAlert(isHighAlert);
        order.setStatus(MedicationOrderStatus.SUBMITTED);
        order.setSubmittedBy(username);
        order.setSubmittedDate(Instant.now());

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][SUBMIT] saved -> id={} status={} submittedBy={}",
                saved.getId(), saved.getStatus(), saved.getSubmittedBy());

        return saved;
    }

    public UrgentCareMedicationOrder administer(
            Long orderId,
            String username,
            Instant actualAdministerTime
    ) {

        LOG.debug(
                "[STATUS][ADMINISTER] request -> orderId={} username={}",
                orderId,
                username
        );

        UrgentCareMedicationOrder order = getOrder(orderId);

        if (order.getStatus() != MedicationOrderStatus.SUBMITTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "patient_ucc_medication_order",
                    "Administer is allowed only when status is SUBMITTED"
            );
        }

        if (Boolean.TRUE.equals(order.getIsHighAlert())) {
            order.setStatus(MedicationOrderStatus.WAITING_DOUBLE_CHECK);
        } else {
            order.setStatus(MedicationOrderStatus.ADMINISTERED);
        }

        order.setActualAdministerTime(actualAdministerTime);

        order.setAdministeredBy(username);
        order.setAdministeredDate(Instant.now());

        return urgentCareMedicationOrderRepository.save(order);
    }

    public UrgentCareMedicationOrder doubleCheck(Long orderId, String username) {
        LOG.debug("[STATUS][DOUBLE_CHECK] request -> orderId={} username={}", orderId, username);

        UrgentCareMedicationOrder order = getOrder(orderId);

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

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][DOUBLE_CHECK] saved -> id={} status={} doubleCheckedBy={}",
                saved.getId(), saved.getStatus(), saved.getDoubleCheckedBy());

        return saved;
    }

    public UrgentCareMedicationOrder discard(Long orderId, String username, String discardReason) {
        LOG.debug("[STATUS][DISCARD] request -> orderId={} username={} reason={}", orderId, username, discardReason);

        UrgentCareMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.DISCARDED);

        order.setStatus(MedicationOrderStatus.DISCARDED);
        order.setDiscardedBy(username);
        order.setDiscardedDate(Instant.now());
        order.setDiscardReason(discardReason);

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][DISCARD] saved -> id={} status={} discardedBy={}",
                saved.getId(), saved.getStatus(), saved.getDiscardedBy());

        return saved;
    }

    public UrgentCareMedicationOrder cancel(Long orderId, String username, String cancellationReason) {
        LOG.debug("[STATUS][CANCEL] request -> orderId={} username={} reason={}", orderId, username, cancellationReason);

        UrgentCareMedicationOrder order = getOrder(orderId);

        ensureTransition(order, MedicationOrderStatus.CANCELLED);

        order.setStatus(MedicationOrderStatus.CANCELLED);
        order.setCancelledBy(username);
        order.setCancelledDate(Instant.now());
        order.setCancellationReason(cancellationReason);

        UrgentCareMedicationOrder saved = urgentCareMedicationOrderRepository.save(order);

        LOG.debug("[STATUS][CANCEL] saved -> id={} status={} cancelledBy={}",
                saved.getId(), saved.getStatus(), saved.getCancelledBy());

        return saved;
    }

    private UrgentCareMedicationOrder getOrder(Long orderId) {
        LOG.debug("[STATUS][GET_ORDER] request -> orderId={}", orderId);

        UrgentCareMedicationOrder order = urgentCareMedicationOrderRepository.findById(orderId)
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

    private void ensureTransition(UrgentCareMedicationOrder order, MedicationOrderStatus to) {
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

    private void notificationForUCCMedicationOrderCreated(UrgentCareMedicationOrder urgentCareMedicationOrder, Patient patient, PatientEncounter encounter) {
        if (urgentCareMedicationOrder == null || patient == null || encounter == null) {
            return;
        }

        Long departmentId = urgentCareMedicationOrder.getEncounter().getDepartmentId();
        DepartmentDTO departmentDTO = departmentHelper.getDepartment(departmentId);

        FacilityDTO facilityDTO = facilityHelper.getFacility(urgentCareMedicationOrder.getEncounter().getFacilityId());


        if (departmentId == null) {
            LOG.warn(
                    "Skip Urgent Care Medication Order created notification because  department is missing. orderId={}",
                    urgentCareMedicationOrder.getId()
            );
            return;
        }
        String login = SecurityUtils.getCurrentUserLogin().orElse(null);
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule =
                notificationHelper.resolveRecipients(departmentId, login, urgentCareMedicationOrder.getCreatedBy(), urgentCareMedicationOrder.getPatient(), null, false);

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("order_id", urgentCareMedicationOrder.getId());
        data.put("patient_id", patient.getId());
        data.put("patient_name", notificationHelper.getPatientName(patient));
        data.put("encounter_id", encounter.getId());

        data.put("facility_id", urgentCareMedicationOrder.getEncounter().getFacilityId());
        data.put("facility_name", facilityDTO.name());

        data.put("department_id", urgentCareMedicationOrder.getEncounter().getDepartmentId());
        data.put("department_name", departmentDTO.name());

        data.put("instructionType", urgentCareMedicationOrder.getInstructionType() != null ? urgentCareMedicationOrder.getInstructionType().toString() : "");
        data.put("instructionText", urgentCareMedicationOrder.getInstructionText() != null ? urgentCareMedicationOrder.getInstructionText() : "");
        data.put("dose", urgentCareMedicationOrder.getDose() != null ? urgentCareMedicationOrder.getDose().toString() : "");
        data.put("doseUnit", urgentCareMedicationOrder.getDoseUnit() != null ? urgentCareMedicationOrder.getDoseUnit() : "");
        data.put("route", urgentCareMedicationOrder.getRoute() != null ? urgentCareMedicationOrder.getRoute() : "");
        data.put("frequency", urgentCareMedicationOrder.getFrequency() != null ? urgentCareMedicationOrder.getFrequency() : "");
        data.put("isHighAlert", urgentCareMedicationOrder.getIsHighAlert() != null ? urgentCareMedicationOrder.getIsHighAlert() : "");
        data.put("status", urgentCareMedicationOrder.getStatus() != null ? urgentCareMedicationOrder.getStatus().toString() : "");

        notificationHelper.sendNotification(
                null,
                NotificationCode.URGENT_CARE_MEDICATION_ORDER_CREATED,
                recipientsByRule,
                data,
                "URGENT_CARE_MEDICATION_ORDER",
                urgentCareMedicationOrder.getId()
        );

    }

    public UrgentCareMedicationOrder setActualAdministerTime(
            Long orderId,
            Instant actualAdministerTime
    ) {
        LOG.debug(
                "[SERVICE][SET_ACTUAL_ADMINISTER_TIME] orderId={} actualAdministerTime={}",
                orderId,
                actualAdministerTime
        );

        UrgentCareMedicationOrder order = getOrder(orderId);

        order.setActualAdministerTime(actualAdministerTime);

        UrgentCareMedicationOrder saved =
                urgentCareMedicationOrderRepository.save(order);

        LOG.debug(
                "[SERVICE][SET_ACTUAL_ADMINISTER_TIME] saved orderId={}",
                saved.getId()
        );

        return saved;
    }
}