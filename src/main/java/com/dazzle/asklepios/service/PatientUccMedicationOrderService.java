package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientUccMedicationOrder;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.PatientUccMedicationOrderRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.PatientUccMedicationOrderCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.uccmedicationorders.PatientUccMedicationOrderUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PatientUccMedicationOrderService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientUccMedicationOrderService.class);

    private final PatientUccMedicationOrderRepository patientUccMedicationOrderRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository encounterRepository;

    public PatientUccMedicationOrderService(
            PatientUccMedicationOrderRepository patientUccMedicationOrderRepository,
            PatientRepository patientRepository,
            PatientEncounterRepository encounterRepository
    ) {
        this.patientUccMedicationOrderRepository = patientUccMedicationOrderRepository;
        this.patientRepository = patientRepository;
        this.encounterRepository = encounterRepository;
    }

    public PatientUccMedicationOrder create(PatientUccMedicationOrderCreateDTO dto) {
        LOG.debug("Request to create PatientUccMedicationOrder: {}", dto);

        PatientUccMedicationOrder order = new PatientUccMedicationOrder();
        order.setActiveIngredientId(dto.activeIngredientId());
        order.setInstructionType(dto.instructionType());
        order.setInstructionText(dto.instructionText());
        order.setDose(dto.dose());
        order.setDoseUnit(dto.doseUnit());
        order.setRoute(dto.route());
        order.setFrequency(dto.frequency());

        order.setStatus(MedicationOrderStatus.NEW);
        order.setPatient(patientRepository.getReferenceById(dto.patientId()));
        order.setEncounter(encounterRepository.getReferenceById(dto.encounterId()));

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(order);

        LOG.debug("[SERVICE][CREATE] saved -> id={} patientId={} encounterId={} status={}",
                saved.getId(),
                dto.patientId(),
                dto.encounterId(),
                saved.getStatus());

        return saved;
    }

    public PatientUccMedicationOrder update(PatientUccMedicationOrder existing, PatientUccMedicationOrderUpdateDTO dto) {
        LOG.debug("[SERVICE][UPDATE] request -> existingId={} payload={}", existing.getId(), dto);

        existing.setActiveIngredientId(dto.activeIngredientId());
        existing.setInstructionType(dto.instructionType());
        existing.setInstructionText(dto.instructionText());
        existing.setDose(dto.dose());
        existing.setDoseUnit(dto.doseUnit());
        existing.setRoute(dto.route());
        existing.setFrequency(dto.frequency());

        PatientUccMedicationOrder saved = patientUccMedicationOrderRepository.save(existing);

        LOG.debug("[SERVICE][UPDATE] saved -> id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional(readOnly = true)
    public PatientUccMedicationOrder findOne(Long id) {
        LOG.debug("[SERVICE][FIND_ONE] request -> id={}", id);

        PatientUccMedicationOrder order = patientUccMedicationOrderRepository.findById(id)
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
    public Page<PatientUccMedicationOrder> findAll(Pageable pageable) {
        LOG.debug("[SERVICE][FIND_ALL] request -> pageable={}", pageable);

        Page<PatientUccMedicationOrder> page = patientUccMedicationOrderRepository.findAll(pageable);

        LOG.debug("[SERVICE][FIND_ALL] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }

    @Transactional(readOnly = true)
    public Page<PatientUccMedicationOrder> findByStatus(MedicationOrderStatus status, Pageable pageable) {
        LOG.debug("[SERVICE][FIND_BY_STATUS] request -> status={} pageable={}", status, pageable);

        Page<PatientUccMedicationOrder> page = patientUccMedicationOrderRepository.findByStatus(status, pageable);

        LOG.debug("[SERVICE][FIND_BY_STATUS] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }

    @Transactional(readOnly = true)
    public Page<PatientUccMedicationOrder> filter(
            Specification<PatientUccMedicationOrder> spec,
            Pageable pageable
    ) {
        LOG.debug("[SERVICE][FILTER] request -> pageable={}", pageable);

        Page<PatientUccMedicationOrder> page = patientUccMedicationOrderRepository.findAll(spec, pageable);

        LOG.debug("[SERVICE][FILTER] response -> size={} totalElements={} totalPages={}",
                page.getContent().size(),
                page.getTotalElements(),
                page.getTotalPages());

        return page;
    }


}