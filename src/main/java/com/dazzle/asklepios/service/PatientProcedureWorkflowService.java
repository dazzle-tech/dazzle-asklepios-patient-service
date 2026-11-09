package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientProcedure;
import com.dazzle.asklepios.domain.PatientProcedureComplication;
import com.dazzle.asklepios.domain.PatientProcedureMedication;
import com.dazzle.asklepios.domain.PatientProcedurePerformance;
import com.dazzle.asklepios.domain.PatientProcedurePostCare;
import com.dazzle.asklepios.domain.PatientProcedurePreAssessment;
import com.dazzle.asklepios.domain.PatientProcedureServiceEquipment;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PatientProcedureStaff;
import com.dazzle.asklepios.domain.PatientProcedureVitalMonitoring;
import com.dazzle.asklepios.domain.enumeration.Priority;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.ProcedureLevel;
import com.dazzle.asklepios.repository.PatientProcedureComplicationRepository;
import com.dazzle.asklepios.repository.PatientProcedureMedicationRepository;
import com.dazzle.asklepios.repository.PatientProcedurePerformanceRepository;
import com.dazzle.asklepios.repository.PatientProcedurePostCareRepository;
import com.dazzle.asklepios.repository.PatientProcedurePreAssessmentRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientProcedureServiceEquipmentRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PatientProcedureStaffRepository;
import com.dazzle.asklepios.repository.PatientProcedureVitalMonitoringRepository;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureComplicationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureMedicationDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePerformanceDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePostCareDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedurePreAssessmentDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureRegistrationDTO;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureStaffDTO;
import com.dazzle.asklepios.service.dto.patientProcedure.PatientProcedureVitalMonitoringDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import jakarta.persistence.criteria.Expression;
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
    private final PatientProcedureComplicationRepository complicationRepository;
    private final PatientProcedureVitalMonitoringRepository vitalMonitoringRepository;
    private final PatientProcedureServiceEquipmentRepository equipmentRepository;
    private final PatientProcedurePostCareRepository postCareRepository;
    private final EncounterReopenGuard encounterReopenGuard;
    private final PatientServiceAndProductService serviceAndProductService;
    private final PatientServiceAndProductRepository serviceAndProductRepository;

    public PatientProcedureWorkflowService(
            PatientProcedureRepository procedureRepository,
            PatientProcedureStaffRepository staffRepository,
            PatientProcedurePreAssessmentRepository preAssessmentRepository,
            PatientProcedurePerformanceRepository performanceRepository,
            PatientProcedureMedicationRepository medicationRepository,
            PatientProcedureComplicationRepository complicationRepository,
            PatientProcedureVitalMonitoringRepository vitalMonitoringRepository,
            PatientProcedureServiceEquipmentRepository equipmentRepository,
            PatientProcedurePostCareRepository postCareRepository,
            EncounterReopenGuard encounterReopenGuard,
            PatientServiceAndProductService serviceAndProductService,
            PatientServiceAndProductRepository serviceAndProductRepository
    ) {
        this.procedureRepository = procedureRepository;
        this.staffRepository = staffRepository;
        this.preAssessmentRepository = preAssessmentRepository;
        this.performanceRepository = performanceRepository;
        this.medicationRepository = medicationRepository;
        this.complicationRepository = complicationRepository;
        this.vitalMonitoringRepository = vitalMonitoringRepository;
        this.equipmentRepository = equipmentRepository;
        this.postCareRepository = postCareRepository;
        this.encounterReopenGuard = encounterReopenGuard;
        this.serviceAndProductService = serviceAndProductService;
        this.serviceAndProductRepository = serviceAndProductRepository;
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
            Priority priority,
            ProcedureLevel procedureLevel,
            Long patientId,
            Long procedureId,
            boolean includeCancelled,
            Pageable pageable
    ) {
        Specification<PatientProcedure> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // An unscheduled request is listed by the day it was created
            Expression<Instant> listedDate = cb.coalesce(
                    root.<Instant>get("scheduledDateTime"),
                    root.<Instant>get("createdDate")
            );
            if (fromDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(listedDate, fromDate));
            }
            if (toDate != null) {
                predicates.add(cb.lessThanOrEqualTo(listedDate, toDate));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            } else if (!includeCancelled) {
                predicates.add(cb.notEqual(root.get("status"), ProcStatus.CANCELLED));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            if (procedureLevel != null) {
                predicates.add(cb.equal(root.get("procedureLevel"), procedureLevel));
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
    public List<PatientProcedureComplication> findComplications(Long procedureId) {
        return complicationRepository.findByPatientProcedure_IdOrderByIdAsc(procedureId);
    }

    public PatientProcedureComplication addComplication(Long procedureId, PatientProcedureComplicationDTO dto) {
        PatientProcedureComplication complication = new PatientProcedureComplication();
        complication.setPatientProcedure(findWritableProcedure(procedureId));
        apply(complication, dto);
        return complicationRepository.save(complication);
    }

    public PatientProcedureComplication updateComplication(
            Long procedureId,
            Long complicationId,
            PatientProcedureComplicationDTO dto
    ) {
        findWritableProcedure(procedureId);
        PatientProcedureComplication complication = complicationRepository
                .findByIdAndPatientProcedure_Id(complicationId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound);
        apply(complication, dto);
        return complicationRepository.save(complication);
    }

    public void deleteComplication(Long procedureId, Long complicationId) {
        findWritableProcedure(procedureId);
        complicationRepository.delete(complicationRepository.findByIdAndPatientProcedure_Id(complicationId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound));
    }

    @Transactional(readOnly = true)
    public List<PatientProcedureVitalMonitoring> findVitalMonitoring(Long procedureId) {
        return vitalMonitoringRepository.findByPatientProcedure_IdOrderByRecordedAtAscIdAsc(procedureId);
    }

    public PatientProcedureVitalMonitoring addVitalMonitoring(Long procedureId, PatientProcedureVitalMonitoringDTO dto) {
        PatientProcedureVitalMonitoring reading = new PatientProcedureVitalMonitoring();
        reading.setPatientProcedure(findWritableProcedure(procedureId));
        apply(reading, dto);
        return vitalMonitoringRepository.save(reading);
    }

    public void deleteVitalMonitoring(Long procedureId, Long readingId) {
        findWritableProcedure(procedureId);
        vitalMonitoringRepository.delete(vitalMonitoringRepository.findByIdAndPatientProcedure_Id(readingId, procedureId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound));
    }

    /** The services and products added from this procedure, without cancelled ones. */
    @Transactional(readOnly = true)
    public List<PatientServiceAndProduct> findEquipment(Long procedureId) {
        List<Long> itemIds = equipmentRepository.findByPatientProcedure_IdOrderByIdAsc(procedureId).stream()
                .map(PatientProcedureServiceEquipment::getServiceProductId)
                .toList();
        return serviceAndProductRepository.findAllById(itemIds).stream()
                .filter(item -> item.getPaymentStatus() != PaymentStatus.CANCELLED)
                .sorted(java.util.Comparator.comparing(PatientServiceAndProduct::getId))
                .toList();
    }

    /**
     * Adds the item to the encounter's services and products, billed like one added from the
     * encounter itself, and records that it came from this procedure.
     */
    public PatientServiceAndProduct addEquipment(Long procedureId, PatientServiceProductCreateDTO dto) {
        PatientProcedure procedure = findWritableProcedure(procedureId);
        PatientServiceAndProduct item = serviceAndProductService.create(new PatientServiceProductCreateDTO(
                procedure.getPatient().getId(),
                procedure.getEncounter().getId(),
                dto.billingItemType(),
                dto.brandMedicationId(),
                dto.diagnosticTestId(),
                dto.serviceId(),
                dto.procedureId(),
                dto.quantity(),
                dto.unitPrice(),
                dto.currency(),
                ServiceSource.SERVICE_AND_PRODUCT,
                null,
                dto.notes(),
                dto.acceptUncoveredAsCash(),
                dto.uomGroupUnitId(),
                dto.vaccineBrandId()
        ));

        PatientProcedureServiceEquipment link = new PatientProcedureServiceEquipment();
        link.setPatientProcedure(procedure);
        link.setServiceProductId(item.getId());
        equipmentRepository.save(link);
        return item;
    }

    /** Cancels the item the same way the encounter screen does, then forgets the link. */
    public void deleteEquipment(Long procedureId, Long serviceProductId) {
        findWritableProcedure(procedureId);
        PatientProcedureServiceEquipment link = equipmentRepository
                .findByPatientProcedure_IdAndServiceProductId(procedureId, serviceProductId)
                .orElseThrow(PatientProcedureWorkflowService::childNotFound);
        serviceAndProductService.remove(serviceProductId);
        equipmentRepository.delete(link);
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
        entity.setAdditionalNotes(dto.additionalNotes());
        entity.setHomeInstruction(dto.homeInstruction());
        entity.setHomeInstructionNotes(dto.homeInstructionNotes());
    }

    private static void apply(PatientProcedureMedication entity, PatientProcedureMedicationDTO dto) {
        entity.setActiveIngredientId(dto.activeIngredientId());
        entity.setDose(dto.dose());
        entity.setUnit(dto.unit());
    }

    private static void apply(PatientProcedureComplication entity, PatientProcedureComplicationDTO dto) {
        entity.setComplicationType(dto.complicationType());
        entity.setSeverity(dto.severity());
        entity.setActionsTaken(dto.actionsTaken());
    }

    private static void apply(PatientProcedureVitalMonitoring entity, PatientProcedureVitalMonitoringDTO dto) {
        entity.setRecordedAt(dto.recordedAt());
        entity.setHeartRate(dto.heartRate());
        entity.setOxygenSaturation(dto.oxygenSaturation());
        entity.setBloodPressureSystolic(dto.bloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.bloodPressureDiastolic());
        entity.setRespiratoryRate(dto.respiratoryRate());
        entity.setTemperature(dto.temperature());
        entity.setEtco2(dto.etco2());
        entity.setFluidsGiven(dto.fluidsGiven());
        entity.setUrineOutput(dto.urineOutput());
    }

    private static void apply(PatientProcedurePostCare entity, PatientProcedurePostCareDTO dto) {
        entity.getDiagnosisIcdIds().clear();
        if (dto.diagnosisIcdIds() != null) {
            entity.getDiagnosisIcdIds().addAll(dto.diagnosisIcdIds());
        }
        entity.setPatientCondition(dto.patientCondition());
        entity.setBloodPressureSystolic(dto.bloodPressureSystolic());
        entity.setBloodPressureDiastolic(dto.bloodPressureDiastolic());
        entity.setHeartRate(dto.heartRate());
        entity.setTemperature(dto.temperature());
        entity.setOxygenSaturation(dto.oxygenSaturation());
        entity.setPainAssessmentType(dto.painAssessmentType());
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
