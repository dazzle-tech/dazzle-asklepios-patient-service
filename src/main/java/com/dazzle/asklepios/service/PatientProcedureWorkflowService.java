package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientProcedureMedication;
import com.dazzle.asklepios.domain.PatientProcedurePerformance;
import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import com.dazzle.asklepios.domain.PatientProcedurePreAssessment;
import com.dazzle.asklepios.domain.PatientProcedureServiceEquipment;
import com.dazzle.asklepios.domain.PatientProcedureStaff;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.repository.PatientProcedureMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedurePerformanceRepository;
import com.dazzle.asklepios.repository.PatientProcedurePostCareRepository;
import com.dazzle.asklepios.repository.PatientProcedurePreAssessmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureServiceEquipmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureStaffRepository;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureMedicationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePerformanceDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePostCareDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePreAssessmentDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureRegistrationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureServiceEquipmentDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStaffDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Clinical workflow of a {@link PatientProcedure}: registration, staff, assessment,
 * performance, post-procedure care, equipment and status changes.
 */
@Service
@Transactional
public class PatientProcedureWorkflowService {

    private static final String ENTITY = "procedure";

    private static final Map<ProcStatus, Set<ProcStatus>> ALLOWED_TRANSITIONS = Map.of(
            ProcStatus.REQUESTED, EnumSet.of(ProcStatus.AWAITING_CONSENT, ProcStatus.STARTED),
            ProcStatus.AWAITING_CONSENT, EnumSet.of(ProcStatus.STARTED),
            ProcStatus.STARTED, EnumSet.of(ProcStatus.COMPLETED)
    );

    private final PatientProcedureRepository procedureRepository;
    private final PatientProcedureStaffRepository staffRepository;
    private final PatientProcedurePreAssessmentRepository preAssessmentRepository;
    private final PatientProcedurePerformanceRepository performanceRepository;
    private final PatientProcedureMedicationRepository medicationRepository;
    private final PatientProcedureServiceEquipmentRepository equipmentRepository;
    private final PatientProcedurePostCareRepository postCareRepository;
    private final EncounterReopenGuard encounterReopenGuard;

    public PatientProcedureWorkflowService(
            PatientProcedureRepository procedureRepository,
            PatientProcedureStaffRepository staffRepository,
            PatientProcedurePreAssessmentRepository preAssessmentRepository,
            PatientProcedurePerformanceRepository performanceRepository,
            PatientProcedureMedicationRepository medicationRepository,
            PatientProcedureServiceEquipmentRepository equipmentRepository,
            PatientProcedurePostCareRepository postCareRepository,
            EncounterReopenGuard encounterReopenGuard
    ) {
        this.procedureRepository = procedureRepository;
        this.staffRepository = staffRepository;
        this.preAssessmentRepository = preAssessmentRepository;
        this.performanceRepository = performanceRepository;
        this.medicationRepository = medicationRepository;
        this.equipmentRepository = equipmentRepository;
        this.postCareRepository = postCareRepository;
        this.encounterReopenGuard = encounterReopenGuard;
    }

    private PatientProcedure findProcedure(Long procedureId) {
        return procedureRepository.findById(procedureId)
                .orElseThrow(() -> new NotFoundAlertException("Procedure not found", ENTITY, "notfound"));
    }

    private PatientProcedure findWritableProcedure(Long procedureId) {
        PatientProcedure procedure = findProcedure(procedureId);
        if (procedure.getStatus() == ProcStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cancelled procedure cannot be updated", ENTITY, "already.cancelled");
        }
        encounterReopenGuard.rejectIfOpenReopenSession(procedure.getEncounter().getId());
        return procedure;
    }

    private static NotFoundAlertException childNotFound() {
        return new NotFoundAlertException("Procedure record not found", ENTITY, "notfound");
    }

    public PatientProcedure changeStatus(Long procedureId, ProcStatus target) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        if (!ALLOWED_TRANSITIONS.getOrDefault(procedure.getStatus(), Set.of()).contains(target)) {
            throw new BadRequestAlertException(
                    "Status cannot change from " + procedure.getStatus() + " to " + target,
                    ENTITY,
                    "invalid.status.transition"
            );
        }
        procedure.setStatus(target);
        return procedureRepository.save(procedure);
    }

    public PatientProcedure saveRegistration(Long procedureId, PatientProcedureRegistrationDTO dto) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        procedure.setConsentForm(dto.consentForm());
        procedure.setRegistrationDateTime(dto.registrationDateTime());
        procedure.setPerformingDepartmentId(dto.performingDepartmentId());
        procedure.setPerformingPractitionerId(dto.performingPractitionerId());
        return procedureRepository.save(procedure);
    }

    @Transactional(readOnly = true)
    public Page<PatientProcedure> search(
            Instant fromDate,
            Instant toDate,
            ProcStatus status,
            Long patientId,
            Long procedureId,
            boolean includeCancelled,
            Pageable pageable
    ) {
        Specification<PatientProcedure> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledDateTime"), fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledDateTime"), toDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (!includeCancelled) {
                predicates.add(cb.notEqual(root.get("status"), ProcStatus.CANCELLED));
            }
            if (patientId != null) {
                predicates.add(cb.equal(root.get("patient").get("id"), patientId));
            }
            if (procedureId != null) {
                predicates.add(cb.equal(root.get("procedureId"), procedureId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return procedureRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public List<PatientProcedureStaff> findStaff(Long procedureId) {
        return staffRepository.findByPatientProcedure_IdOrderByIdAsc(procedureId);
    }

    public PatientProcedureStaff addStaff(Long procedureId, PatientProcedureStaffDTO dto) {
        PatientProcedureStaff staff = new PatientProcedureStaff();
        staff.setPatientProcedure(findWritableProcedure(procedureId));
        apply(staff, dto);
        return staffRepository.save(staff);
    }

    public PatientProcedureStaff updateStaff(Long procedureId, Long staffId, PatientProcedureStaffDTO dto) {
        findWritableProcedure(procedureId);
        PatientProcedureStaff staff = staffRepository.findByIdAndPatientProcedure_Id(staffId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound);
        apply(staff, dto);
        return staffRepository.save(staff);
    }

    public void deleteStaff(Long procedureId, Long staffId) {
        findWritableProcedure(procedureId);
        staffRepository.delete(staffRepository.findByIdAndPatientProcedure_Id(staffId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound));
    }

    @Transactional(readOnly = true)
    public Optional<PatientProcedurePreAssessment> findPreAssessment(Long procedureId) {
        return preAssessmentRepository.findByPatientProcedure_Id(procedureId);
    }

    public PatientProcedurePreAssessment savePreAssessment(Long procedureId, PatientProcedurePreAssessmentDTO dto) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        PatientProcedurePreAssessment entity = preAssessmentRepository.findByPatientProcedure_Id(procedureId)
                .orElseGet(PatientProcedurePreAssessment::new);
        entity.setPatientProcedure(procedure);
        apply(entity, dto);
        return preAssessmentRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<PatientProcedurePerformance> findPerformance(Long procedureId) {
        return performanceRepository.findByPatientProcedure_Id(procedureId);
    }

    public PatientProcedurePerformance savePerformance(Long procedureId, PatientProcedurePerformanceDTO dto) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        PatientProcedurePerformance entity = performanceRepository.findByPatientProcedure_Id(procedureId)
                .orElseGet(PatientProcedurePerformance::new);
        entity.setPatientProcedure(procedure);
        apply(entity, dto);
        return performanceRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<PatientProcedurePostCare> findPostCare(Long procedureId) {
        return postCareRepository.findByPatientProcedure_Id(procedureId);
    }

    public PatientProcedurePostCare savePostCare(Long procedureId, PatientProcedurePostCareDTO dto) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        PatientProcedurePostCare entity = postCareRepository.findByPatientProcedure_Id(procedureId)
                .orElseGet(PatientProcedurePostCare::new);
        entity.setPatientProcedure(procedure);
        apply(entity, dto);
        return postCareRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<PatientProcedureMedication> findMedications(Long procedureId) {
        return medicationRepository.findByPatientProcedure_IdOrderByIdAsc(procedureId);
    }

    public PatientProcedureMedication addMedication(Long procedureId, PatientProcedureMedicationDTO dto) {
        PatientProcedureMedication medication = new PatientProcedureMedication();
        medication.setPatientProcedure(findWritableProcedure(procedureId));
        apply(medication, dto);
        return medicationRepository.save(medication);
    }

    public PatientProcedureMedication updateMedication(
            Long procedureId,
            Long medicationId,
            PatientProcedureMedicationDTO dto
    ) {
        findWritableProcedure(procedureId);
        PatientProcedureMedication medication = medicationRepository
                .findByIdAndPatientProcedure_Id(medicationId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound);
        apply(medication, dto);
        return medicationRepository.save(medication);
    }

    public void deleteMedication(Long procedureId, Long medicationId) {
        findWritableProcedure(procedureId);
        medicationRepository.delete(medicationRepository.findByIdAndPatientProcedure_Id(medicationId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound));
    }

    @Transactional(readOnly = true)
    public List<PatientProcedureServiceEquipment> findEquipment(Long procedureId) {
        return equipmentRepository.findByPatientProcedure_IdOrderByIdAsc(procedureId);
    }

    public PatientProcedureServiceEquipment addEquipment(Long procedureId, PatientProcedureServiceEquipmentDTO dto) {
        PatientProcedureServiceEquipment equipment = new PatientProcedureServiceEquipment();
        equipment.setPatientProcedure(findWritableProcedure(procedureId));
        apply(equipment, dto);
        return equipmentRepository.save(equipment);
    }

    public void deleteEquipment(Long procedureId, Long equipmentId) {
        findWritableProcedure(procedureId);
        equipmentRepository.delete(equipmentRepository.findByIdAndPatientProcedure_Id(equipmentId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound));
    }

    private static void apply(PatientProcedureStaff entity, PatientProcedureStaffDTO dto) {
        entity.setUserId(dto.userId());
        entity.setResponsibility(dto.responsibility());
        entity.setIsPresent(Boolean.TRUE.equals(dto.isPresent()));
    }

    private static void apply(PatientProcedurePreAssessment entity, PatientProcedurePreAssessmentDTO dto) {
        entity.setFastingRequired(Boolean.TRUE.equals(dto.fastingRequired()));
        entity.setPatientPrepared(Boolean.TRUE.equals(dto.patientPrepared()));
        entity.setSpecialInstructions(dto.specialInstructions());
        entity.setBloodPressureSystolic(dto.bloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.bloodPressureDiastolic());
        entity.setHeartRate(dto.heartRate());
        entity.setTemperature(dto.temperature());
        entity.setOxygenSaturation(dto.oxygenSaturation());
        entity.setPatientIdentityVerified(Boolean.TRUE.equals(dto.patientIdentityVerified()));
        entity.setConsentConfirmed(Boolean.TRUE.equals(dto.consentConfirmed()));
        entity.setProcedureSiteMarked(Boolean.TRUE.equals(dto.procedureSiteMarked()));
        entity.setAllergiesConfirmed(Boolean.TRUE.equals(dto.allergiesConfirmed()));
        entity.setPatientPremedicated(Boolean.TRUE.equals(dto.patientPremedicated()));
        entity.setEquipmentCountingDone(Boolean.TRUE.equals(dto.equipmentCountingDone()));
        entity.setAsaScore(dto.asaScore());
        entity.setAirwayGrade(dto.airwayGrade());
    }

    private static void apply(PatientProcedurePerformance entity, PatientProcedurePerformanceDTO dto) {
        entity.setActualStartTime(dto.actualStartTime());
        entity.setActualEndTime(dto.actualEndTime());
        entity.setAnesthesiaUsed(Boolean.TRUE.equals(dto.anesthesiaUsed()));
        entity.setAnesthesiaType(dto.anesthesiaType());
        entity.setAnesthesiaStartTime(dto.anesthesiaStartTime());
        entity.setAnesthesiaEndTime(dto.anesthesiaEndTime());
        entity.setAnesthesiaAdministeredById(dto.anesthesiaAdministeredById());
        entity.setTimeOut(Boolean.TRUE.equals(dto.timeOut()));
        entity.setProcedureOutcome(dto.procedureOutcome());
        entity.setObservations(dto.observations());
        entity.setComplicationType(dto.complicationType());
        entity.setComplicationSeverity(dto.complicationSeverity());
        entity.setActionsTaken(dto.actionsTaken());
        entity.setAdditionalNotes(dto.additionalNotes());
        entity.setHomeInstruction(dto.homeInstruction());
        entity.setHomeInstructionNotes(dto.homeInstructionNotes());
    }

    private static void apply(PatientProcedureMedication entity, PatientProcedureMedicationDTO dto) {
        entity.setActiveIngredientId(dto.activeIngredientId());
        entity.setDose(dto.dose());
        entity.setUnit(dto.unit());
    }

    private static void apply(PatientProcedureServiceEquipment entity, PatientProcedureServiceEquipmentDTO dto) {
        entity.setServiceId(dto.serviceId());
    }

    private static void apply(PatientProcedurePostCare entity, PatientProcedurePostCareDTO dto) {
        entity.setDiagnosisIcdId(dto.diagnosisIcdId());
        entity.setDiagnosisDescription(dto.diagnosisDescription());
        entity.setPatientCondition(dto.patientCondition());
        entity.setBloodPressureSystolic(dto.bloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.bloodPressureDiastolic());
        entity.setHeartRate(dto.heartRate());
        entity.setTemperature(dto.temperature());
        entity.setOxygenSaturation(dto.oxygenSaturation());
        entity.setPainScore(dto.painScore());
        entity.setPainDescription(dto.painDescription());
        entity.setRecoveryNotes(dto.recoveryNotes());
        entity.setAdditionalObservations(dto.additionalObservations());
        entity.setEquipmentCountDone(Boolean.TRUE.equals(dto.equipmentCountDone()));
        entity.setCountStatus(dto.countStatus());
        entity.setNauseaVomiting(Boolean.TRUE.equals(dto.nauseaVomiting()));
        entity.setAwakeAndOriented(Boolean.TRUE.equals(dto.awakeAndOriented()));
        entity.setToleratingOralFluids(Boolean.TRUE.equals(dto.toleratingOralFluids()));
        entity.setAmbulatingIndependently(Boolean.TRUE.equals(dto.ambulatingIndependently()));
        entity.setVoidedUrine(Boolean.TRUE.equals(dto.voidedUrine()));
        entity.setNoActiveBleeding(Boolean.TRUE.equals(dto.noActiveBleeding()));
        entity.setPainScore4(Boolean.TRUE.equals(dto.painScore4()));
        entity.setActivity(dto.activity());
        entity.setRespiration(dto.respiration());
        entity.setCirculation(dto.circulation());
        entity.setConsciousness(dto.consciousness());
        entity.setOxygenSaturationLevel(dto.oxygenSaturationLevel());
        entity.setAldreteScore(dto.aldreteScore());
    }
}
