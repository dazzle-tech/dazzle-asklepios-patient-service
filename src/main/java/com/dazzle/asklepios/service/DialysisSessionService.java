package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DialysisSession;
import com.dazzle.asklepios.domain.enumeration.DialysisAccessSite;
import com.dazzle.asklepios.domain.enumeration.DialysisAnticoagulation;
import com.dazzle.asklepios.repository.DialysisSessionRepository;
import com.dazzle.asklepios.repository.NephrologyTreatmentPlanRepository;
import com.dazzle.asklepios.service.dto.DialysisSessionCreateDTO;
import com.dazzle.asklepios.service.dto.DialysisSessionUpdateDTO;
import com.dazzle.asklepios.service.vm.DialysisSessionResponseVM;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class DialysisSessionService {

    private static final String ENTITY_NAME = "dialysisSession";

    private final DialysisSessionRepository repository;
    private final NephrologyTreatmentPlanRepository treatmentPlanRepository;

    public DialysisSessionResponseVM create(
            DialysisSessionCreateDTO dto
    ) {
        Long treatmentPlanId = requireTreatmentPlanIdForPatient(
                dto.getTreatmentPlanId(),
                dto.getPatientId()
        );

        assertNoActiveSessionForPlan(
                dto.getPatientId(),
                dto.getEncounterId(),
                treatmentPlanId,
                null
        );

        DialysisSession entity =
                DialysisSession.builder()
                        .patientId(dto.getPatientId())
                        .encounterId(dto.getEncounterId())
                        .chairStation(dto.getChairStation())
                        .machine(dto.getMachine())
                        .date(dto.getDate())
                        .startTime(dto.getStartTime())
                        .endTime(dto.getEndTime())
                        .shift(dto.getShift())
                        .assignedNurseId(dto.getAssignedNurseId())
                        .nephrologistId(dto.getNephrologistId())
                        .treatmentPlanId(treatmentPlanId)
                        .preWeight(dto.getPreWeight())
                        .dryWeight(dto.getDryWeight())
                        .weightGain(dto.getWeightGain())
                        .interdialyticWeightGain(dto.getInterdialyticWeightGain())
                        .bloodPressureSystolic(dto.getBloodPressureSystolic())
                        .bloodPressureDiastolic(dto.getBloodPressureDiastolic())
                        .heartRate(dto.getHeartRate())
                        .temperature(dto.getTemperature())
                        .respiratoryRate(dto.getRespiratoryRate())
                        .oxygenSaturation(dto.getOxygenSaturation())
                        .symptoms(dto.getSymptoms())
                        .edema(dto.getEdema())
                        .preAccessCondition(dto.getPreAccessCondition())
                        .generalCondition(dto.getGeneralCondition())
                        .dialysisDuration(dto.getDialysisDuration())
                        .durationType(dto.getDurationType())
                        .bloodFlowRate(dto.getBloodFlowRate())
                        .dialysateFlowRate(dto.getDialysateFlowRate())
                        .ultrafiltrationGoal(dto.getUltrafiltrationGoal())
                        .dialysateComposition(dto.getDialysateComposition())
                        .sodium(dto.getSodium())
                        .potassium(dto.getPotassium())
                        .calcium(dto.getCalcium())
                        .dialysisTemperature(dto.getDialysisTemperature())
                        .heparinDose(dto.getHeparinDose())
                        .anticoagulation(dto.getAnticoagulation())
                        .otherAnticoagulation(
                                resolveOtherAnticoagulation(
                                        dto.getAnticoagulation(),
                                        dto.getOtherAnticoagulation()
                                )
                        )
                        .targetDryWeight(dto.getTargetDryWeight())
                        .accessType(dto.getAccessType())
                        .accessSite(dto.getAccessSite())
                        .otherAccessSite(
                                resolveOtherAccessSite(
                                        dto.getAccessSite(),
                                        dto.getOtherAccessSite()
                                )
                        )
                        .infection(dto.getInfection())
                        .bleeding(dto.getBleeding())
                        .thrill(dto.getThrill())
                        .bruit(dto.getBruit())
                        .dressing(dto.getDressing())
                        .catheterCondition(dto.getCatheterCondition())
                        .postWeight(dto.getPostWeight())
                        .postBloodPressureSystolic(dto.getPostBloodPressureSystolic())
                        .postBloodPressureDiastolic(dto.getPostBloodPressureDiastolic())
                        .postPulse(dto.getPostPulse())
                        .postTemperature(dto.getPostTemperature())
                        .totalUfRemoved(dto.getTotalUfRemoved())
                        .actualTreatmentDuration(dto.getActualTreatmentDuration())
                        .postAccessCondition(dto.getPostAccessCondition())
                        .disposition(dto.getDisposition())
                        .postComplications(dto.getPostComplications())
                        .patientCondition(dto.getPatientCondition())
                        .targetUf(dto.getTargetUf())
                        .actualUf(dto.getActualUf())
                        .ufDifference(dto.getUfDifference())
                        .complications(dto.getComplications())
                        .complicationNotes(dto.getComplicationNotes())
                        .isActive(true)
                        .build();

        return toResponseVM(repository.save(entity));
    }

    public DialysisSessionResponseVM update(
            DialysisSessionUpdateDTO dto
    ) {
        DialysisSession entity = repository
                .findById(dto.getId())
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "Dialysis session not found: " + dto.getId()
                        )
                );

        Long treatmentPlanId = requireTreatmentPlanIdForPatient(
                dto.getTreatmentPlanId(),
                entity.getPatientId()
        );

        if (
                entity.getTreatmentPlanId() != null &&
                !Objects.equals(entity.getTreatmentPlanId(), treatmentPlanId)
        ) {
            throw new BadRequestAlertException(
                    "Treatment Plan cannot be changed for an existing Dialysis Session",
                    ENTITY_NAME,
                    "treatmentplan.immutable"
            );
        }

        assertNoActiveSessionForPlan(
                entity.getPatientId(),
                entity.getEncounterId(),
                treatmentPlanId,
                entity.getId()
        );

        entity.setChairStation(dto.getChairStation());
        entity.setMachine(dto.getMachine());
        entity.setDate(dto.getDate());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setShift(dto.getShift());
        entity.setAssignedNurseId(dto.getAssignedNurseId());
        entity.setNephrologistId(dto.getNephrologistId());
        entity.setTreatmentPlanId(treatmentPlanId);
        entity.setPreWeight(dto.getPreWeight());
        entity.setDryWeight(dto.getDryWeight());
        entity.setWeightGain(dto.getWeightGain());
        entity.setInterdialyticWeightGain(dto.getInterdialyticWeightGain());
        entity.setBloodPressureSystolic(dto.getBloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.getBloodPressureDiastolic());
        entity.setHeartRate(dto.getHeartRate());
        entity.setTemperature(dto.getTemperature());
        entity.setRespiratoryRate(dto.getRespiratoryRate());
        entity.setOxygenSaturation(dto.getOxygenSaturation());
        entity.setSymptoms(dto.getSymptoms());
        entity.setEdema(dto.getEdema());
        entity.setPreAccessCondition(dto.getPreAccessCondition());
        entity.setGeneralCondition(dto.getGeneralCondition());
        entity.setDialysisDuration(dto.getDialysisDuration());
        entity.setDurationType(dto.getDurationType());
        entity.setBloodFlowRate(dto.getBloodFlowRate());
        entity.setDialysateFlowRate(dto.getDialysateFlowRate());
        entity.setUltrafiltrationGoal(dto.getUltrafiltrationGoal());
        entity.setDialysateComposition(dto.getDialysateComposition());
        entity.setSodium(dto.getSodium());
        entity.setPotassium(dto.getPotassium());
        entity.setCalcium(dto.getCalcium());
        entity.setDialysisTemperature(dto.getDialysisTemperature());
        entity.setHeparinDose(dto.getHeparinDose());
        entity.setAnticoagulation(dto.getAnticoagulation());
        entity.setOtherAnticoagulation(
                resolveOtherAnticoagulation(
                        dto.getAnticoagulation(),
                        dto.getOtherAnticoagulation()
                )
        );
        entity.setTargetDryWeight(dto.getTargetDryWeight());
        entity.setAccessType(dto.getAccessType());
        entity.setAccessSite(dto.getAccessSite());
        entity.setOtherAccessSite(
                resolveOtherAccessSite(
                        dto.getAccessSite(),
                        dto.getOtherAccessSite()
                )
        );
        entity.setInfection(dto.getInfection());
        entity.setBleeding(dto.getBleeding());
        entity.setThrill(dto.getThrill());
        entity.setBruit(dto.getBruit());
        entity.setDressing(dto.getDressing());
        entity.setCatheterCondition(dto.getCatheterCondition());
        entity.setPostWeight(dto.getPostWeight());
        entity.setPostBloodPressureSystolic(dto.getPostBloodPressureSystolic());
        entity.setPostBloodPressureDiastolic(dto.getPostBloodPressureDiastolic());
        entity.setPostPulse(dto.getPostPulse());
        entity.setPostTemperature(dto.getPostTemperature());
        entity.setTotalUfRemoved(dto.getTotalUfRemoved());
        entity.setActualTreatmentDuration(dto.getActualTreatmentDuration());
        entity.setPostAccessCondition(dto.getPostAccessCondition());
        entity.setDisposition(dto.getDisposition());
        entity.setPostComplications(dto.getPostComplications());
        entity.setPatientCondition(dto.getPatientCondition());
        entity.setTargetUf(dto.getTargetUf());
        entity.setActualUf(dto.getActualUf());
        entity.setUfDifference(dto.getUfDifference());
        entity.setComplications(dto.getComplications());
        entity.setComplicationNotes(dto.getComplicationNotes());

        return toResponseVM(repository.save(entity));
    }

    /**
     * Legacy single-session lookup. Returns the most recently modified active
     * session for the patient/encounter when multiple plan-scoped sessions exist.
     */
    @Transactional(readOnly = true)
    public Optional<DialysisSessionResponseVM>
    findByPatientAndEncounter(
            Long patientId,
            Long encounterId
    ) {
        return repository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrueOrderByLastModifiedDateDesc(
                        patientId,
                        encounterId
                )
                .map(this::toResponseVM);
    }

    @Transactional(readOnly = true)
    public Optional<DialysisSessionResponseVM> findById(Long id) {
        return repository.findById(id).map(this::toResponseVM);
    }

    @Transactional(readOnly = true)
    public Optional<DialysisSessionResponseVM> findByIdForContext(
            Long id,
            Long patientId,
            Long encounterId
    ) {
        return repository
                .findByIdAndPatientIdAndEncounterIdAndIsActiveTrue(
                        id,
                        patientId,
                        encounterId
                )
                .map(this::toResponseVM);
    }

    @Transactional(readOnly = true)
    public Page<DialysisSessionResponseVM> findByPatientAndEncounter(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId,
            Pageable pageable
    ) {
        Page<DialysisSession> page =
                treatmentPlanId != null
                        ? repository.findByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrue(
                                patientId,
                                encounterId,
                                treatmentPlanId,
                                pageable
                        )
                        : repository.findByPatientIdAndEncounterIdAndIsActiveTrue(
                                patientId,
                                encounterId,
                                pageable
                        );

        return page.map(this::toResponseVM);
    }

    @Transactional(readOnly = true)
    public List<DialysisSessionResponseVM> findAllByPatientEncounterAndTreatmentPlan(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId
    ) {
        return repository
                .findByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrueOrderByLastModifiedDateDesc(
                        patientId,
                        encounterId,
                        treatmentPlanId
                )
                .stream()
                .map(this::toResponseVM)
                .toList();
    }

    private void assertNoActiveSessionForPlan(
            Long patientId,
            Long encounterId,
            Long treatmentPlanId,
            Long excludeSessionId
    ) {
        boolean exists;

        if (treatmentPlanId == null) {
            exists = excludeSessionId == null
                    ? repository.existsByPatientIdAndEncounterIdAndTreatmentPlanIdIsNullAndIsActiveTrue(
                            patientId,
                            encounterId
                    )
                    : repository.existsByPatientIdAndEncounterIdAndTreatmentPlanIdIsNullAndIsActiveTrueAndIdNot(
                            patientId,
                            encounterId,
                            excludeSessionId
                    );
        } else {
            exists = excludeSessionId == null
                    ? repository.existsByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrue(
                            patientId,
                            encounterId,
                            treatmentPlanId
                    )
                    : repository.existsByPatientIdAndEncounterIdAndTreatmentPlanIdAndIsActiveTrueAndIdNot(
                            patientId,
                            encounterId,
                            treatmentPlanId,
                            excludeSessionId
                    );
        }

        if (exists) {
            throw new BadRequestAlertException(
                    treatmentPlanId == null
                            ? "Active dialysis session without treatment plan already exists for this patient and encounter"
                            : "Active dialysis session already exists for this patient, encounter, and treatment plan",
                    ENTITY_NAME,
                    "session.exists"
            );
        }
    }

    /**
     * Requires a non-null Treatment Plan that exists, is active, and belongs to the patient.
     * Never returns null and never silently substitutes another plan.
     */
    private Long requireTreatmentPlanIdForPatient(
            Long treatmentPlanId,
            Long patientId
    ) {
        if (treatmentPlanId == null) {
            throw new BadRequestAlertException(
                    "Treatment Plan is required for Dialysis Session",
                    ENTITY_NAME,
                    "treatmentplan.required"
            );
        }

        return treatmentPlanRepository
                .findByIdAndPatientIdAndIsActiveTrue(treatmentPlanId, patientId)
                .map(plan -> plan.getId())
                .orElseThrow(() ->
                        new BadRequestAlertException(
                                "Treatment plan does not belong to this patient: " +
                                        treatmentPlanId,
                                ENTITY_NAME,
                                "treatmentplan.invalid"
                        )
                );
    }

    private String resolveOtherAnticoagulation(
            DialysisAnticoagulation anticoagulation,
            String otherAnticoagulation
    ) {
        if (anticoagulation != DialysisAnticoagulation.OTHER) {
            return null;
        }

        return otherAnticoagulation == null ? null : otherAnticoagulation.trim();
    }

    private String resolveOtherAccessSite(
            DialysisAccessSite accessSite,
            String otherAccessSite
    ) {
        if (accessSite != DialysisAccessSite.OTHER) {
            return null;
        }

        return otherAccessSite == null ? null : otherAccessSite.trim();
    }

    private DialysisSessionResponseVM toResponseVM(
            DialysisSession entity
    ) {
        return DialysisSessionResponseVM.builder()
                .id(entity.getId())
                .patientId(entity.getPatientId())
                .encounterId(entity.getEncounterId())
                .chairStation(entity.getChairStation())
                .machine(entity.getMachine())
                .date(entity.getDate())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .shift(entity.getShift())
                .assignedNurseId(entity.getAssignedNurseId())
                .nephrologistId(entity.getNephrologistId())
                .treatmentPlanId(entity.getTreatmentPlanId())
                .preWeight(entity.getPreWeight())
                .dryWeight(entity.getDryWeight())
                .weightGain(entity.getWeightGain())
                .interdialyticWeightGain(entity.getInterdialyticWeightGain())
                .bloodPressureSystolic(entity.getBloodPressureSystolic())
                .bloodPressureDiastolic(entity.getBloodPressureDiastolic())
                .heartRate(entity.getHeartRate())
                .temperature(entity.getTemperature())
                .respiratoryRate(entity.getRespiratoryRate())
                .oxygenSaturation(entity.getOxygenSaturation())
                .symptoms(entity.getSymptoms())
                .edema(entity.getEdema())
                .preAccessCondition(entity.getPreAccessCondition())
                .generalCondition(entity.getGeneralCondition())
                .dialysisDuration(entity.getDialysisDuration())
                .durationType(entity.getDurationType())
                .bloodFlowRate(entity.getBloodFlowRate())
                .dialysateFlowRate(entity.getDialysateFlowRate())
                .ultrafiltrationGoal(entity.getUltrafiltrationGoal())
                .dialysateComposition(entity.getDialysateComposition())
                .sodium(entity.getSodium())
                .potassium(entity.getPotassium())
                .calcium(entity.getCalcium())
                .dialysisTemperature(entity.getDialysisTemperature())
                .heparinDose(entity.getHeparinDose())
                .anticoagulation(entity.getAnticoagulation())
                .otherAnticoagulation(entity.getOtherAnticoagulation())
                .targetDryWeight(entity.getTargetDryWeight())
                .accessType(entity.getAccessType())
                .accessSite(entity.getAccessSite())
                .otherAccessSite(entity.getOtherAccessSite())
                .infection(entity.getInfection())
                .bleeding(entity.getBleeding())
                .thrill(entity.getThrill())
                .bruit(entity.getBruit())
                .dressing(entity.getDressing())
                .catheterCondition(entity.getCatheterCondition())
                .postWeight(entity.getPostWeight())
                .postBloodPressureSystolic(entity.getPostBloodPressureSystolic())
                .postBloodPressureDiastolic(entity.getPostBloodPressureDiastolic())
                .postPulse(entity.getPostPulse())
                .postTemperature(entity.getPostTemperature())
                .totalUfRemoved(entity.getTotalUfRemoved())
                .actualTreatmentDuration(entity.getActualTreatmentDuration())
                .postAccessCondition(entity.getPostAccessCondition())
                .disposition(entity.getDisposition())
                .postComplications(entity.getPostComplications())
                .patientCondition(entity.getPatientCondition())
                .targetUf(entity.getTargetUf())
                .actualUf(entity.getActualUf())
                .ufDifference(entity.getUfDifference())
                .complications(entity.getComplications())
                .complicationNotes(entity.getComplicationNotes())
                .isActive(entity.getIsActive())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .lastModifiedBy(entity.getLastModifiedBy())
                .lastModifiedDate(entity.getLastModifiedDate())
                .build();
    }
}
