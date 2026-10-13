package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DentalPlannedTreatment;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.PlannedTreatmentType;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalPlannedTreatmentRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.dentalChart.DentalChartToothSummaryDTO;
import com.dazzle.asklepios.service.dto.dentalChart.DentalTreatmentPlanResponseDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentDTO;
import com.dazzle.asklepios.service.dto.dentalChart.PlannedTreatmentSaveDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class DentalPlannedTreatmentService {

    private final DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public DentalPlannedTreatmentService(
            DentalPlannedTreatmentRepository dentalPlannedTreatmentRepository,
            DentalChartToothRepository dentalChartToothRepository,
            PatientEncounterRepository patientEncounterRepository
    ) {
        this.dentalPlannedTreatmentRepository = dentalPlannedTreatmentRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.patientEncounterRepository = patientEncounterRepository;
    }

    @Transactional(readOnly = true)
    public DentalTreatmentPlanResponseDTO getTreatmentPlanForEncounter(Long encounterId) {
        PatientEncounter encounter = getEncounter(encounterId);

        List<PlannedTreatmentDTO> draftTreatments = dentalPlannedTreatmentRepository
                .findByPatientIdAndTypeAndCancelledFalseOrderByVisitNumberAsc(
                        encounter.getPatient().getId(),
                        PlannedTreatmentType.DRAFT
                )
                .stream()
                .map(treatment -> toDTO(treatment))
                .toList();

        List<PlannedTreatmentDTO> currentTreatments = dentalPlannedTreatmentRepository
                .findByEncounterIdAndTypeAndCancelledFalseOrderByCreatedDateAsc(
                        encounterId,
                        PlannedTreatmentType.CURRENT
                )
                .stream()
                .map(treatment -> toDTO(treatment))
                .toList();

        return new DentalTreatmentPlanResponseDTO(draftTreatments, currentTreatments);
    }

    public PlannedTreatmentDTO createPlannedTreatment(PlannedTreatmentSaveDTO dto) {
        validateToothRequiredForCurrent(dto);

        DentalPlannedTreatment entity = DentalPlannedTreatment.builder()
                .patientId(dto.patientId())
                .encounterId(dto.encounterId())
                .type(dto.type())
                .visitNumber(dto.visitNumber())
                .cdtCodeId(dto.cdtCodeId())
                .toothId(dto.toothId())
                .note(dto.note())
                .surface(dto.surface())
                .fees(dto.fees() != null ? dto.fees() : java.math.BigDecimal.ZERO)
                .insurance(dto.insurance() != null ? dto.insurance() : java.math.BigDecimal.ZERO)
                .discount(dto.discount() != null ? dto.discount() : java.math.BigDecimal.ZERO)
                .cancelled(false)
                .build();

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        return toDTO(saved);
    }

    public PlannedTreatmentDTO updatePlannedTreatment(Long id, PlannedTreatmentSaveDTO dto) {
        validateToothRequiredForCurrent(dto);

        DentalPlannedTreatment entity = getPlannedTreatment(id);

        entity.setPatientId(dto.patientId());
        entity.setEncounterId(dto.encounterId());
        entity.setType(dto.type());
        entity.setVisitNumber(dto.visitNumber());
        entity.setCdtCodeId(dto.cdtCodeId());
        entity.setToothId(dto.toothId());
        entity.setNote(dto.note());
        entity.setSurface(dto.surface());
        entity.setFees(dto.fees() != null ? dto.fees() : java.math.BigDecimal.ZERO);
        entity.setInsurance(dto.insurance() != null ? dto.insurance() : java.math.BigDecimal.ZERO);
        entity.setDiscount(dto.discount() != null ? dto.discount() : java.math.BigDecimal.ZERO);

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        return toDTO(saved);
    }

    public PlannedTreatmentDTO cancelPlannedTreatment(Long id, String cancellationReason) {
        DentalPlannedTreatment entity = getPlannedTreatment(id);

        if (entity.getType() != PlannedTreatmentType.DRAFT) {
            throw new BadRequestAlertException(
                    "Only DRAFT planned treatment can be cancelled",
                    "dentalPlannedTreatment",
                    "onlyDraftCancellable"
            );
        }
        if (cancellationReason == null || cancellationReason.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Cancellation reason is required",
                    "dentalPlannedTreatment",
                    "cancellationReasonRequired"
            );
        }

        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));

        entity.setCancelled(true);
        entity.setCancelledBy(currentUser);
        entity.setCancelledDate(Instant.now());
        entity.setCancellationReason(cancellationReason);

        DentalPlannedTreatment saved = dentalPlannedTreatmentRepository.save(entity);
        return toDTO(saved);
    }

    private void validateToothRequiredForCurrent(PlannedTreatmentSaveDTO dto) {
        if (dto.type() == PlannedTreatmentType.CURRENT && dto.toothId() == null) {
            throw new BadRequestAlertException(
                    "A tooth is required for a CURRENT planned treatment",
                    "dentalPlannedTreatment",
                    "toothRequiredForCurrent"
            );
        }
    }

    private PlannedTreatmentDTO toDTO(DentalPlannedTreatment treatment) {
        DentalChartToothSummaryDTO toothSummary = null;
        if (treatment.getToothId() != null) {
            toothSummary = dentalChartToothRepository.findById(treatment.getToothId())
                    .map(DentalChartToothSummaryDTO::fromEntity)
                    .orElse(null);
        }
        return PlannedTreatmentDTO.fromEntity(treatment, toothSummary);
    }

    private PatientEncounter getEncounter(Long encounterId) {
        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "encounterNotFound",
                        "dentalPlannedTreatment",
                        "Encounter not found with id " + encounterId
                ));
    }

    private DentalPlannedTreatment getPlannedTreatment(Long id) {
        return dentalPlannedTreatmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Planned treatment not found: " + id,
                        "dentalPlannedTreatment",
                        "notfound"
                ));
    }
}
