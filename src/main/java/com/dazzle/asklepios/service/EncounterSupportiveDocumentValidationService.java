package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.FormEntriesClient;
import com.dazzle.asklepios.client.setup.SupportiveDocumentRequirementClient;
import com.dazzle.asklepios.client.setup.dto.FormEntryDTO;
import com.dazzle.asklepios.client.setup.dto.PayorSupportiveDocumentRequirementDTO;
import com.dazzle.asklepios.client.setup.dto.SupportiveDocumentRequirementCatalogDTO;
import com.dazzle.asklepios.domain.DentalChart;
import com.dazzle.asklepios.domain.DentalChartTooth;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.EncounterVaccinationStatus;
import com.dazzle.asklepios.domain.enumeration.FLACCPainScaleStatus;
import com.dazzle.asklepios.domain.enumeration.MedicationOrderStatus;
import com.dazzle.asklepios.domain.enumeration.PatientAllergyStatus;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.PatientWarningStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.PrescriptionStatus;
import com.dazzle.asklepios.domain.enumeration.ProcStatus;
import com.dazzle.asklepios.domain.enumeration.ReferralStatus;
import com.dazzle.asklepios.domain.enumeration.SupportiveDocumentRequirementCode;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import com.dazzle.asklepios.repository.AdditionalMeasurementsRepository;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ChiefComplainRepository;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.repository.CurrentMedicationRepository;
import com.dazzle.asklepios.repository.DentalChartProgressNoteRepository;
import com.dazzle.asklepios.repository.DentalChartRepository;
import com.dazzle.asklepios.repository.DentalChartToothRepository;
import com.dazzle.asklepios.repository.DentalProcedureRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.DialysisSessionRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.EncounterPlanRepository;
import com.dazzle.asklepios.repository.EncounterVaccinationRepository;
import com.dazzle.asklepios.repository.FamilyHistoryRepository;
import com.dazzle.asklepios.repository.FLACCPainScaleRepository;
import com.dazzle.asklepios.repository.GeneralAssessmentRepository;
import com.dazzle.asklepios.repository.GlasgowComaScaleAssessmentRepository;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.NephrologyKidneyAssessmentRepository;
import com.dazzle.asklepios.repository.NephrologyRenalFunctionRepository;
import com.dazzle.asklepios.repository.NephrologyTreatmentPlanRepository;
import com.dazzle.asklepios.repository.PainAssessmentRepository;
import com.dazzle.asklepios.repository.PatientAllergiesRepository;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientObservationsComplaintsRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionRepository;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.PatientProcedureRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PatientWarningsRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.ReferralRequestRepository;
import com.dazzle.asklepios.repository.ReviewOfSystemRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.repository.ToothActionRepository;
import com.dazzle.asklepios.repository.UrgentCareMedicationOrderRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.service.dto.patientEncounter.DiagnosticCheckoutApproval;
import com.dazzle.asklepios.service.dto.patientEncounter.MissingSupportiveDocumentRequirementDTO;
import com.dazzle.asklepios.service.dto.patientEncounter.PatientEncounterCompletionValidationDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class EncounterSupportiveDocumentValidationService {

    private static final String ENTITY_NAME = "patientEncounter";
    private static final Logger LOG =
            LoggerFactory.getLogger(EncounterSupportiveDocumentValidationService.class);

    private final PatientInsuranceRepository patientInsuranceRepository;
    private final SupportiveDocumentRequirementClient supportiveDocumentRequirementClient;
    private final FormEntriesClient formEntriesClient;
    private final InsuranceCheckoutDiagnosticApprovalService insuranceCheckoutDiagnosticApprovalService;

    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final EncounterAssessmentRepository encounterAssessmentRepository;
    private final EncounterPlanRepository encounterPlanRepository;
    private final PatientProblemRepository patientProblemRepository;
    private final FamilyHistoryRepository familyHistoryRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final CurrentMedicationRepository currentMedicationRepository;
    private final SurgicalHistoryRepository surgicalHistoryRepository;
    private final SocialHistoryRepository socialHistoryRepository;
    private final ProgressNoteRepository progressNoteRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final AdditionalMeasurementsRepository additionalMeasurementsRepository;
    private final PainAssessmentRepository painAssessmentRepository;
    private final PatientObservationsComplaintsRepository patientObservationsComplaintsRepository;
    private final GlasgowComaScaleAssessmentRepository glasgowComaScaleAssessmentRepository;
    private final FLACCPainScaleRepository flaccPainScaleRepository;
    private final DialysisSessionRepository dialysisSessionRepository;
    private final EncounterVaccinationRepository encounterVaccinationRepository;
    private final NephrologyKidneyAssessmentRepository nephrologyKidneyAssessmentRepository;
    private final NephrologyRenalFunctionRepository nephrologyRenalFunctionRepository;
    private final NephrologyTreatmentPlanRepository nephrologyTreatmentPlanRepository;
    private final DentalChartRepository dentalChartRepository;
    private final DentalChartToothRepository dentalChartToothRepository;
    private final ToothActionRepository toothActionRepository;
    private final DentalChartProgressNoteRepository dentalChartProgressNoteRepository;
    private final DentalProcedureRepository dentalProcedureRepository;
    private final ChiefComplainRepository chiefComplainRepository;
    private final GeneralAssessmentRepository generalAssessmentRepository;
    private final PatientAllergiesRepository patientAllergiesRepository;
    private final PatientWarningsRepository patientWarningsRepository;
    private final ConsultationRepository consultationRepository;
    private final PatientProcedureRepository patientProcedureRepository;
    private final ReferralRequestRepository referralRequestRepository;
    private final PatientPrescriptionRepository patientPrescriptionRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    private final UrgentCareMedicationOrderRepository urgentCareMedicationOrderRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;
    private final ReviewOfSystemRepository reviewOfSystemRepository;

    public EncounterSupportiveDocumentValidationService(
            PatientInsuranceRepository patientInsuranceRepository,
            SupportiveDocumentRequirementClient supportiveDocumentRequirementClient,
            FormEntriesClient formEntriesClient,
            InsuranceCheckoutDiagnosticApprovalService insuranceCheckoutDiagnosticApprovalService,
            PatientDiagnosisRepository patientDiagnosisRepository,
            EncounterAssessmentRepository encounterAssessmentRepository,
            EncounterPlanRepository encounterPlanRepository,
            PatientProblemRepository patientProblemRepository,
            FamilyHistoryRepository familyHistoryRepository,
            HospitalizationRepository hospitalizationRepository,
            CurrentMedicationRepository currentMedicationRepository,
            SurgicalHistoryRepository surgicalHistoryRepository,
            SocialHistoryRepository socialHistoryRepository,
            ProgressNoteRepository progressNoteRepository,
            VitalSignsRepository vitalSignsRepository,
            BodyMeasurementsRepository bodyMeasurementsRepository,
            AdditionalMeasurementsRepository additionalMeasurementsRepository,
            PainAssessmentRepository painAssessmentRepository,
            PatientObservationsComplaintsRepository patientObservationsComplaintsRepository,
            GlasgowComaScaleAssessmentRepository glasgowComaScaleAssessmentRepository,
            FLACCPainScaleRepository flaccPainScaleRepository,
            DialysisSessionRepository dialysisSessionRepository,
            EncounterVaccinationRepository encounterVaccinationRepository,
            NephrologyKidneyAssessmentRepository nephrologyKidneyAssessmentRepository,
            NephrologyRenalFunctionRepository nephrologyRenalFunctionRepository,
            NephrologyTreatmentPlanRepository nephrologyTreatmentPlanRepository,
            DentalChartRepository dentalChartRepository,
            DentalChartToothRepository dentalChartToothRepository,
            ToothActionRepository toothActionRepository,
            DentalChartProgressNoteRepository dentalChartProgressNoteRepository,
            DentalProcedureRepository dentalProcedureRepository,
            ChiefComplainRepository chiefComplainRepository,
            GeneralAssessmentRepository generalAssessmentRepository,
            PatientAllergiesRepository patientAllergiesRepository,
            PatientWarningsRepository patientWarningsRepository,
            ConsultationRepository consultationRepository,
            PatientProcedureRepository patientProcedureRepository,
            ReferralRequestRepository referralRequestRepository,
            PatientPrescriptionRepository patientPrescriptionRepository,
            DiagnosticOrderRepository diagnosticOrderRepository,
            DiagnosticOrderTestRepository diagnosticOrderTestRepository,
            DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository,
            UrgentCareMedicationOrderRepository urgentCareMedicationOrderRepository,
            PatientServiceAndProductRepository patientServiceAndProductRepository,
            ReviewOfSystemRepository reviewOfSystemRepository
    ) {
        this.patientInsuranceRepository = patientInsuranceRepository;
        this.supportiveDocumentRequirementClient = supportiveDocumentRequirementClient;
        this.formEntriesClient = formEntriesClient;
        this.insuranceCheckoutDiagnosticApprovalService = insuranceCheckoutDiagnosticApprovalService;
        this.patientDiagnosisRepository = patientDiagnosisRepository;
        this.encounterAssessmentRepository = encounterAssessmentRepository;
        this.encounterPlanRepository = encounterPlanRepository;
        this.patientProblemRepository = patientProblemRepository;
        this.familyHistoryRepository = familyHistoryRepository;
        this.hospitalizationRepository = hospitalizationRepository;
        this.currentMedicationRepository = currentMedicationRepository;
        this.surgicalHistoryRepository = surgicalHistoryRepository;
        this.socialHistoryRepository = socialHistoryRepository;
        this.progressNoteRepository = progressNoteRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.bodyMeasurementsRepository = bodyMeasurementsRepository;
        this.additionalMeasurementsRepository = additionalMeasurementsRepository;
        this.painAssessmentRepository = painAssessmentRepository;
        this.patientObservationsComplaintsRepository = patientObservationsComplaintsRepository;
        this.glasgowComaScaleAssessmentRepository = glasgowComaScaleAssessmentRepository;
        this.flaccPainScaleRepository = flaccPainScaleRepository;
        this.dialysisSessionRepository = dialysisSessionRepository;
        this.encounterVaccinationRepository = encounterVaccinationRepository;
        this.nephrologyKidneyAssessmentRepository = nephrologyKidneyAssessmentRepository;
        this.nephrologyRenalFunctionRepository = nephrologyRenalFunctionRepository;
        this.nephrologyTreatmentPlanRepository = nephrologyTreatmentPlanRepository;
        this.dentalChartRepository = dentalChartRepository;
        this.dentalChartToothRepository = dentalChartToothRepository;
        this.toothActionRepository = toothActionRepository;
        this.dentalChartProgressNoteRepository = dentalChartProgressNoteRepository;
        this.dentalProcedureRepository = dentalProcedureRepository;
        this.chiefComplainRepository = chiefComplainRepository;
        this.generalAssessmentRepository = generalAssessmentRepository;
        this.patientAllergiesRepository = patientAllergiesRepository;
        this.patientWarningsRepository = patientWarningsRepository;
        this.consultationRepository = consultationRepository;
        this.patientProcedureRepository = patientProcedureRepository;
        this.referralRequestRepository = referralRequestRepository;
        this.patientPrescriptionRepository = patientPrescriptionRepository;
        this.diagnosticOrderRepository = diagnosticOrderRepository;
        this.diagnosticOrderTestRepository = diagnosticOrderTestRepository;
        this.diagnosticOrderTestResultRepository = diagnosticOrderTestResultRepository;
        this.urgentCareMedicationOrderRepository = urgentCareMedicationOrderRepository;
        this.patientServiceAndProductRepository = patientServiceAndProductRepository;
        this.reviewOfSystemRepository = reviewOfSystemRepository;
    }

    public PatientEncounterCompletionValidationDTO validate(
            PatientEncounter encounter,
            boolean includeDiagnostics
    ) {
        Long encounterId = encounter.getId();
        boolean insuranceVisit =
                BillingCoverageType.INSURANCE.equals(encounter.getCoverageType());

        if (!insuranceVisit) {
            return nonInsuranceResult(encounter, includeDiagnostics);
        }

        Long payorId = resolvePayorId(encounter);
        PayorSupportiveDocumentRequirementDTO config = fetchConfiguration(payorId);

        if (config.configured() == null) {
            throw new BadRequestAlertException(
                    "Malformed supportive-document configuration response for payor " + payorId,
                    ENTITY_NAME,
                    "completion.supportiveDocumentConfigMalformed"
            );
        }

        ClinicalPresence presence = evaluatePresence(encounter);

        if (!Boolean.TRUE.equals(config.configured())) {
            return legacyInsuranceResult(encounter, presence, includeDiagnostics);
        }

        if (config.requirementCodes() == null) {
            throw new BadRequestAlertException(
                    "Malformed supportive-document configuration: requirementCodes is required when configured.",
                    ENTITY_NAME,
                    "completion.supportiveDocumentConfigMalformed"
            );
        }

        if (config.requirementCodes().isEmpty()) {
            return configuredEmptyResult(encounter, presence, includeDiagnostics);
        }

        return configuredRequirementsResult(encounter, presence, config.requirementCodes(), includeDiagnostics);
    }

    public void requireDocumentationComplete(PatientEncounter encounter) {
        PatientEncounterCompletionValidationDTO validation = validate(encounter, false);
        if (!validation.insuranceVisit()) {
            return;
        }
        if (!validation.canComplete()) {
            throw new BadRequestAlertException(
                    "Encounter documentation requirements are incomplete.",
                    ENTITY_NAME,
                    "complete.documentationIncomplete"
            );
        }
    }

    private Long resolvePayorId(PatientEncounter encounter) {
        Long patientInsuranceId = encounter.getPatientInsuranceId();
        if (patientInsuranceId == null) {
            throw new BadRequestAlertException(
                    "Insurance encounter is missing linked patient insurance.",
                    ENTITY_NAME,
                    "completion.insuranceMissing"
            );
        }

        PatientInsurance insurance = patientInsuranceRepository.findById(patientInsuranceId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Linked patient insurance not found: " + patientInsuranceId,
                        ENTITY_NAME,
                        "completion.insuranceInvalid"
                ));

        Long patientId = encounter.getPatient() != null ? encounter.getPatient().getId() : null;
        if (patientId != null
                && insurance.getPatient() != null
                && insurance.getPatient().getId() != null
                && !patientId.equals(insurance.getPatient().getId())) {
            throw new BadRequestAlertException(
                    "Linked patient insurance does not belong to the encounter patient.",
                    ENTITY_NAME,
                    "completion.insuranceInvalid"
            );
        }

        Long payorId = insurance.getPayorId();
        if (payorId == null || payorId <= 0) {
            throw new BadRequestAlertException(
                    "Linked patient insurance has no valid Payor id.",
                    ENTITY_NAME,
                    "completion.payorUnresolved"
            );
        }
        return payorId;
    }

    private PayorSupportiveDocumentRequirementDTO fetchConfiguration(Long payorId) {
        try {
            PayorSupportiveDocumentRequirementDTO config =
                    supportiveDocumentRequirementClient.getByPayorId(payorId);
            if (config == null) {
                throw new BadRequestAlertException(
                        "Empty supportive-document configuration response for payor " + payorId,
                        ENTITY_NAME,
                        "completion.supportiveDocumentConfigFailed"
                );
            }
            return config;
        } catch (BadRequestAlertException ex) {
            throw ex;
        } catch (FeignException ex) {
            LOG.error("Failed to load supportive-document configuration payorId={}", payorId, ex);
            throw new BadRequestAlertException(
                    "Unable to load supportive-document configuration for payor " + payorId,
                    ENTITY_NAME,
                    "completion.supportiveDocumentConfigFailed"
            );
        } catch (RuntimeException ex) {
            LOG.error("Unexpected error loading supportive-document configuration payorId={}", payorId, ex);
            throw new BadRequestAlertException(
                    "Unable to load supportive-document configuration for payor " + payorId,
                    ENTITY_NAME,
                    "completion.supportiveDocumentConfigFailed"
            );
        }
    }

    private Map<String, SupportiveDocumentRequirementCatalogDTO> fetchCatalogByCode() {
        try {
            List<SupportiveDocumentRequirementCatalogDTO> catalog =
                    supportiveDocumentRequirementClient.getCatalog();
            if (catalog == null) {
                throw new BadRequestAlertException(
                        "Empty supportive-document catalog response.",
                        ENTITY_NAME,
                        "completion.supportiveDocumentCatalogFailed"
                );
            }
            Map<String, SupportiveDocumentRequirementCatalogDTO> byCode = new LinkedHashMap<>();
            for (SupportiveDocumentRequirementCatalogDTO entry : catalog) {
                if (entry == null || entry.code() == null || entry.code().isBlank()) {
                    throw new BadRequestAlertException(
                            "Malformed supportive-document catalog entry.",
                            ENTITY_NAME,
                            "completion.supportiveDocumentCatalogMalformed"
                    );
                }
                byCode.putIfAbsent(entry.code().trim().toUpperCase(Locale.ROOT), entry);
            }
            return byCode;
        } catch (BadRequestAlertException ex) {
            throw ex;
        } catch (FeignException ex) {
            LOG.error("Failed to load supportive-document catalog", ex);
            throw new BadRequestAlertException(
                    "Unable to load supportive-document catalog.",
                    ENTITY_NAME,
                    "completion.supportiveDocumentCatalogFailed"
            );
        } catch (RuntimeException ex) {
            LOG.error("Unexpected error loading supportive-document catalog", ex);
            throw new BadRequestAlertException(
                    "Unable to load supportive-document catalog.",
                    ENTITY_NAME,
                    "completion.supportiveDocumentCatalogFailed"
            );
        }
    }

    private PatientEncounterCompletionValidationDTO configuredRequirementsResult(
            PatientEncounter encounter,
            ClinicalPresence presence,
            List<String> rawCodes,
            boolean includeDiagnostics
    ) {
        Map<String, SupportiveDocumentRequirementCatalogDTO> catalogByCode = fetchCatalogByCode();

        Set<SupportiveDocumentRequirementCode> selected = new LinkedHashSet<>();
        for (String raw : rawCodes) {
            if (raw == null || raw.isBlank()) {
                throw new BadRequestAlertException(
                        "Supportive-document configuration contains a blank requirement code.",
                        ENTITY_NAME,
                        "completion.requirementCode.invalid"
                );
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            SupportiveDocumentRequirementCode code;
            try {
                code = SupportiveDocumentRequirementCode.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestAlertException(
                        "Unsupported supportive document requirement code: " + raw,
                        ENTITY_NAME,
                        "completion.requirementCode.unsupported"
                );
            }
            if (!catalogByCode.containsKey(normalized)) {
                throw new BadRequestAlertException(
                        "Requirement code not present in setup catalog: " + raw,
                        ENTITY_NAME,
                        "completion.requirementCode.unsupported"
                );
            }
            selected.add(code);
        }

        List<MissingSupportiveDocumentRequirementDTO> missingRequirements = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (SupportiveDocumentRequirementCatalogDTO entry : catalogByCode.values()) {
            SupportiveDocumentRequirementCode code;
            try {
                code = SupportiveDocumentRequirementCode.valueOf(entry.code().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (!selected.contains(code)) {
                continue;
            }
            if (isSatisfied(code, presence, encounter.getId())) {
                continue;
            }
            MissingSupportiveDocumentRequirementDTO missingItem =
                    new MissingSupportiveDocumentRequirementDTO(
                            entry.code(),
                            entry.label(),
                            entry.medicalSheetCode(),
                            entry.medicalSheetName(),
                            entry.medicalSheetPath()
                    );
            missingRequirements.add(missingItem);
            missing.add(entry.label() != null && !entry.label().isBlank() ? entry.label() : entry.code());
        }

        return buildDto(
                encounter,
                true,
                presence,
                missing.isEmpty(),
                missing,
                missingRequirements,
                includeDiagnostics
        );
    }

    private boolean isSatisfied(
            SupportiveDocumentRequirementCode code,
            ClinicalPresence presence,
            Long encounterId
    ) {
        return switch (code) {
            case CHIEF_COMPLAINT -> presence.chiefComplaint();
            case HISTORY_OF_PRESENT_ILLNESS -> presence.historyOfPresentIllness();
            case PHYSICAL_EXAMINATION_SUMMARY -> presence.physicalExaminationSummary();
            case REVIEW_OF_SYSTEMS -> presence.reviewOfSystems();
            case PATIENT_DIAGNOSIS -> presence.primaryDiagnosis();
            case ASSESSMENT -> presence.assessment();
            case TREATMENT_PLAN -> presence.treatmentPlan();
            case PATIENT_PROBLEMS -> presence.patientProblems();
            case FAMILY_HISTORY -> presence.familyHistory();
            case HOSPITALIZATIONS -> presence.hospitalizations();
            case CURRENT_MEDICATION -> presence.currentMedication();
            case SURGICAL_HISTORY -> presence.surgicalHistory();
            case SOCIAL_HISTORY -> presence.socialHistoryNonCancelled();
            case VITAL_SIGNS -> presence.vitalSignsActive();
            case BODY_MEASUREMENTS -> presence.bodyMeasurements();
            case PROGRESS_NOTES -> presence.progressNotes();
            case PAIN_ASSESSMENT -> presence.painAssessment();
            case ADDITIONAL_MEASUREMENTS -> presence.additionalMeasurements();
            case PATIENT_OBSERVATIONS_COMPLAINTS -> presence.patientObservationsComplaints();
            case GLASGOW_COMA_SCALE -> presence.glasgowComaScale();
            case FLACC_NEONATES_PAIN_ASSESSMENT -> presence.flaccNeonatesPainAssessment();
            case DIALYSIS_SESSION -> presence.dialysisSession();
            case VACCINATION -> presence.vaccination();
            case NEPHROLOGY_KIDNEY_ASSESSMENT -> presence.nephrologyKidneyAssessment();
            case NEPHROLOGY_RENAL_FUNCTION -> presence.nephrologyRenalFunction();
            case NEPHROLOGY_TREATMENT_PLAN -> presence.nephrologyTreatmentPlan();
            case DENTAL_CARE -> presence.dentalCare();
            case DENTAL_PROCEDURES -> presence.dentalProcedures();
            case FORMS -> hasEncounterFormEntry(encounterId);
            case CHIEF_COMPLAIN -> presence.chiefComplain();
            case GENERAL_ASSESSMENT -> presence.generalAssessment();
            case ALLERGIES -> presence.allergies();
            case MEDICAL_WARNINGS -> presence.medicalWarnings();
            case CONSULTATION -> presence.consultation();
            case PROCEDURES -> presence.procedures();
            case REFERRAL_REQUEST -> presence.referralRequest();
            case PRESCRIPTION -> presence.prescription();
            case DIAGNOSTICS_ORDER -> presence.diagnosticsOrder();
            case DIAGNOSTICS_RESULT -> presence.diagnosticsResult();
            case UCC_MEDICATION_ORDER -> presence.uccMedicationOrder();
            case SERVICEANDPRODUCTS -> presence.serviceAndProducts();
        };
    }

    /**
     * Setup-owned form entries; Feign only when FORMS is selected.
     * Persistence of an encounter-scoped entry is the documented completion signal
     * (FormEntries have no COMPLETED/SUBMITTED status).
     */
    private boolean hasEncounterFormEntry(Long encounterId) {
        try {
            List<FormEntryDTO> entries = formEntriesClient.listByEncounter(encounterId, 0, 1);
            return entries != null && !entries.isEmpty();
        } catch (FeignException ex) {
            LOG.error("Failed to load form entries for encounterId={}", encounterId, ex);
            throw new BadRequestAlertException(
                    "Unable to load form entries for encounter " + encounterId,
                    ENTITY_NAME,
                    "completion.formEntriesFailed"
            );
        } catch (RuntimeException ex) {
            LOG.error("Unexpected error loading form entries encounterId={}", encounterId, ex);
            throw new BadRequestAlertException(
                    "Unable to load form entries for encounter " + encounterId,
                    ENTITY_NAME,
                    "completion.formEntriesFailed"
            );
        }
    }

    private ClinicalPresence evaluatePresence(PatientEncounter encounter) {
        Long encounterId = encounter.getId();
        Long patientId = encounter.getPatient().getId();

        boolean chiefComplaint = !isBlank(encounter.getChiefComplaint());
        boolean historyOfPresentIllness = !isBlank(encounter.getHistoryOfPresentIllness());
        boolean physicalExaminationSummary = !isBlank(encounter.getPhysicalExaminationSummery());
        boolean reviewOfSystems = reviewOfSystemRepository.existsByEncounterId(encounterId);
        boolean primaryDiagnosis = patientDiagnosisRepository.existsByEncounterId(encounterId);
        boolean assessment = encounterAssessmentRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean treatmentPlan = encounterPlanRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounterId)
                .isPresent();

        boolean patientProblems = patientProblemRepository
                .findAllByPatientIdAndStatus(patientId, PatientHistoryStatus.ACTIVE, PageRequest.of(0, 1))
                .hasContent();
        boolean familyHistory = familyHistoryRepository
                .findAllByPatientIdAndStatus(patientId, PatientHistoryStatus.ACTIVE, PageRequest.of(0, 1))
                .hasContent();
        boolean hospitalizations = hospitalizationRepository
                .findAllByPatientIdAndStatusNot(patientId, PatientHistoryStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean currentMedication = currentMedicationRepository
                .findAllByPatientIdAndStatusNot(patientId, PatientHistoryStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean surgicalHistory = surgicalHistoryRepository
                .findAllByPatientIdAndStatusNot(patientId, PatientHistoryStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean socialHistoryNonCancelled = socialHistoryRepository
                .findAllByPatientIdAndStatusNot(patientId, PatientHistoryStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        // Legacy social check: latest row any status
        boolean socialHistoryLegacy = socialHistoryRepository
                .findTopByPatientIdOrderByCreatedDateDesc(patientId)
                .isPresent();

        boolean progressNotes = progressNoteRepository
                .findTopByEncounterIdAndCancelledDateIsNullOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean vitalSignsActive = vitalSignsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean bodyMeasurements = bodyMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean painAssessment = painAssessmentRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean additionalMeasurements = additionalMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean patientObservationsComplaints = patientObservationsComplaintsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean glasgowComaScale = glasgowComaScaleAssessmentRepository
                .findAllByEncounter_IdAndCancelledAtIsNull(encounterId, PageRequest.of(0, 1))
                .hasContent();
        boolean flaccNeonatesPainAssessment = flaccPainScaleRepository
                .findFirstByEncounterIdAndStatusOrderByCreatedDateDesc(
                        encounterId,
                        FLACCPainScaleStatus.ACTIVE
                )
                .isPresent();
        boolean dialysisSession = dialysisSessionRepository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrueOrderByLastModifiedDateDesc(
                        patientId,
                        encounterId
                )
                .isPresent();
        boolean vaccination = encounterVaccinationRepository
                .findByEncounterIdAndStatusNot(
                        encounterId,
                        EncounterVaccinationStatus.CANCELLED,
                        PageRequest.of(0, 1)
                )
                .hasContent();
        boolean nephrologyKidneyAssessment = nephrologyKidneyAssessmentRepository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(patientId, encounterId)
                .isPresent();
        boolean nephrologyRenalFunction = nephrologyRenalFunctionRepository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(patientId, encounterId)
                .isPresent();
        boolean nephrologyTreatmentPlan = nephrologyTreatmentPlanRepository
                .findFirstByPatientIdAndEncounterIdAndIsActiveTrue(patientId, encounterId)
                .isPresent();
        boolean dentalCare = hasDocumentedDentalCare(encounterId);
        boolean dentalProcedures = dentalProcedureRepository
                .existsByEncounter_IdAndCancelledFalse(encounterId);

        boolean chiefComplain = chiefComplainRepository
                .findTopByEncounter_IdOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean generalAssessment = generalAssessmentRepository
                .findTopByEncounter_IdOrderByCreatedDateDesc(encounterId)
                .isPresent();
        boolean allergies = patientAllergiesRepository
                .findByPatientIdAndStatusNot(patientId, PatientAllergyStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean medicalWarnings = patientWarningsRepository
                .findByPatientIdAndStatusNot(patientId, PatientWarningStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean consultation = consultationRepository
                .findByEncounterId(encounterId, PageRequest.of(0, 50))
                .stream()
                .anyMatch(c ->
                        c.getStatus() != ConsultationStatus.CANCELLED
                                && c.getStatus() != ConsultationStatus.REJECTED
                );
        boolean procedures = patientProcedureRepository
                .findByEncounterIdAndStatusNot(encounterId, ProcStatus.CANCELLED, PageRequest.of(0, 1))
                .hasContent();
        boolean referralRequest = referralRequestRepository
                .findByEncounter_Id(encounterId, PageRequest.of(0, 50))
                .stream()
                .anyMatch(r -> r.getStatus() != null && r.getStatus() != ReferralStatus.REJECTED);
        boolean prescription = patientPrescriptionRepository
                .findByPatientIdAndEncounterIdAndStatus(
                        patientId, encounterId, PrescriptionStatus.SUBMITTED, PageRequest.of(0, 1))
                .hasContent()
                || patientPrescriptionRepository
                .findByPatientIdAndEncounterIdAndStatus(
                        patientId, encounterId, PrescriptionStatus.SENT, PageRequest.of(0, 1))
                .hasContent();
        boolean diagnosticsOrder = hasDiagnosticsOrder(encounterId);
        boolean diagnosticsResult = hasDiagnosticsResult(encounterId);
        boolean uccMedicationOrder = urgentCareMedicationOrderRepository
                .findByEncounter_IdAndStatusNotIn(
                        encounterId,
                        List.of(
                                MedicationOrderStatus.NEW,
                                MedicationOrderStatus.CANCELLED,
                                MedicationOrderStatus.DISCARDED
                        ),
                        PageRequest.of(0, 1)
                )
                .hasContent();
        boolean serviceAndProducts = patientServiceAndProductRepository
                .findAllByEncounterIdAndPaymentStatusNot(
                        encounterId,
                        PaymentStatus.CANCELLED,
                        PageRequest.of(0, 1)
                )
                .hasContent();

        boolean medicalHistoryLegacy =
                patientProblems || familyHistory || hospitalizations || currentMedication;

        return new ClinicalPresence(
                chiefComplaint,
                historyOfPresentIllness,
                physicalExaminationSummary,
                reviewOfSystems,
                primaryDiagnosis,
                assessment,
                treatmentPlan,
                patientProblems,
                familyHistory,
                hospitalizations,
                currentMedication,
                surgicalHistory,
                socialHistoryNonCancelled,
                socialHistoryLegacy,
                medicalHistoryLegacy,
                progressNotes,
                vitalSignsActive,
                bodyMeasurements,
                painAssessment,
                additionalMeasurements,
                patientObservationsComplaints,
                glasgowComaScale,
                flaccNeonatesPainAssessment,
                dialysisSession,
                vaccination,
                nephrologyKidneyAssessment,
                nephrologyRenalFunction,
                nephrologyTreatmentPlan,
                dentalCare,
                dentalProcedures,
                chiefComplain,
                generalAssessment,
                allergies,
                medicalWarnings,
                consultation,
                procedures,
                referralRequest,
                prescription,
                diagnosticsOrder,
                diagnosticsResult,
                uccMedicationOrder,
                serviceAndProducts
        );
    }

    /**
     * Submitted (non-draft) order that includes at least one order-test line.
     * Empty headers with saveDraft=false alone do not satisfy.
     */
    private boolean hasDiagnosticsOrder(Long encounterId) {
        List<DiagnosticOrder> orders = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED);
        for (DiagnosticOrder order : orders) {
            boolean submitted = Boolean.FALSE.equals(order.getSaveDraft())
                    || DiagnosticStatus.SUBMITTED.equals(order.getStatus());
            if (!submitted) {
                continue;
            }
            List<Long> testIds = diagnosticOrderTestRepository.findIdsByOrderIdIn(List.of(order.getId()));
            if (testIds != null && !testIds.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Documented result: APPROVED, or READY with an actual numeric/text value.
     * Bare RESULT_READY without values does not satisfy.
     */
    private boolean hasDiagnosticsResult(Long encounterId) {
        List<DiagnosticOrder> orders = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED);
        if (orders.isEmpty()) {
            return false;
        }
        List<Long> orderIds = orders.stream().map(DiagnosticOrder::getId).toList();
        List<Long> orderTestIds = diagnosticOrderTestRepository.findIdsByOrderIdIn(orderIds);
        if (orderTestIds == null || orderTestIds.isEmpty()) {
            return false;
        }
        List<DiagnosticOrderTestResult> results =
                diagnosticOrderTestResultRepository.findByOrderTestIdIn(orderTestIds);
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().anyMatch(result -> {
            boolean hasValue = result.getResultValueNumber() != null
                    || (result.getResultValueText() != null && !result.getResultValueText().isBlank());
            return DiagnosticStatus.RESULT_APPROVED.equals(result.getProcessingStatus())
                    || (DiagnosticStatus.RESULT_READY.equals(result.getProcessingStatus()) && hasValue);
        });
    }

    /**
     * Empty getOrCreate NEW charts do not count. Requires non-cancelled tooth action
     * and/or a non-deleted chart progress note.
     */
    private boolean hasDocumentedDentalCare(Long encounterId) {
        Optional<DentalChart> chartOpt = dentalChartRepository.findByEncounter_Id(encounterId);
        if (chartOpt.isEmpty()) {
            return false;
        }
        Long chartId = chartOpt.get().getId();
        List<DentalChartTooth> teeth = dentalChartToothRepository.findByChartId(chartId);
        List<Long> toothIds = teeth.stream().map(DentalChartTooth::getId).toList();
        boolean hasToothAction = !toothIds.isEmpty()
                && !toothActionRepository.findByToothIdInAndCancelledFalse(toothIds).isEmpty();
        boolean hasProgressNote = !dentalChartProgressNoteRepository
                .findByChartIdAndDeletedAtIsNullOrderByCreatedDateDesc(chartId)
                .isEmpty();
        return hasToothAction || hasProgressNote;
    }

    private PatientEncounterCompletionValidationDTO legacyInsuranceResult(
            PatientEncounter encounter,
            ClinicalPresence presence,
            boolean includeDiagnostics
    ) {
        List<String> missing = new ArrayList<>();

        if (!presence.chiefComplaint()) {
            missing.add("Chief Complaint");
        }
        if (!presence.historyOfPresentIllness()) {
            missing.add("History Of Present Illness");
        }
        if (!presence.physicalExaminationSummary()) {
            missing.add("Physical Examination Summary");
        }
        if (!presence.primaryDiagnosis()) {
            missing.add("Primary Diagnosis");
        }
        if (!presence.assessment()) {
            missing.add("Assessment");
        }
        if (!presence.treatmentPlan()) {
            missing.add("Treatment Plan / Management");
        }
        if (!presence.medicalHistoryLegacy()) {
            missing.add("Medical History");
        }
        if (!presence.surgicalHistory()) {
            missing.add("Surgical History");
        }
        if (!presence.socialHistoryLegacy()) {
            missing.add("Social History");
        }
        if (!presence.progressNotes() && encounter.getEncounterType() != EncounterType.CLINIC) {
            missing.add("Progress Notes");
        }
        boolean legacyVitalSignsAggregate = hasLegacyObservationAggregate(encounter.getId());
        if (!legacyVitalSignsAggregate) {
            missing.add("Vital Signs & Measurements");
        }
        if (!presence.bodyMeasurements()) {
            missing.add("Body Measurements");
        }

        return buildDto(
                encounter,
                true,
                presence,
                missing.isEmpty(),
                missing,
                List.of(),
                includeDiagnostics,
                legacyVitalSignsAggregate
        );
    }

    /**
     * Same OR union as {@code PatientEncounterService.findEncounterIdsWithObservation}
     * for unconfigured-Payor legacy "Vital Signs & Measurements" only.
     */
    private boolean hasLegacyObservationAggregate(Long encounterId) {
        List<Long> ids = List.of(encounterId);
        return !additionalMeasurementsRepository.findDistinctByEncounterIdIn(ids).isEmpty()
                || !vitalSignsRepository.findDistinctByEncounterIdIn(ids).isEmpty()
                || !painAssessmentRepository.findDistinctByEncounterIdIn(ids).isEmpty()
                || !bodyMeasurementsRepository.findDistinctByEncounterIdIn(ids).isEmpty()
                || !patientObservationsComplaintsRepository.findDistinctByEncounterIdIn(ids).isEmpty();
    }

    private PatientEncounterCompletionValidationDTO configuredEmptyResult(
            PatientEncounter encounter,
            ClinicalPresence presence,
            boolean includeDiagnostics
    ) {
        return buildDto(
                encounter,
                true,
                presence,
                true,
                List.of(),
                List.of(),
                includeDiagnostics
        );
    }

    private PatientEncounterCompletionValidationDTO nonInsuranceResult(
            PatientEncounter encounter,
            boolean includeDiagnostics
    ) {
        List<DiagnosticCheckoutApproval> diagnosticApprovals = includeDiagnostics
                ? insuranceCheckoutDiagnosticApprovalService.findUnapproved(encounter.getId())
                : List.of();

        return new PatientEncounterCompletionValidationDTO(
                encounter.getId(),
                encounter.getCoverageType(),
                encounter.getPatientInsuranceId(),
                false,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                Collections.emptyList(),
                diagnosticApprovals,
                Collections.emptyList()
        );
    }

    private PatientEncounterCompletionValidationDTO buildDto(
            PatientEncounter encounter,
            boolean insuranceVisit,
            ClinicalPresence presence,
            boolean canComplete,
            List<String> missing,
            List<MissingSupportiveDocumentRequirementDTO> missingRequirements,
            boolean includeDiagnostics
    ) {
        return buildDto(
                encounter,
                insuranceVisit,
                presence,
                canComplete,
                missing,
                missingRequirements,
                includeDiagnostics,
                presence.vitalSignsActive()
        );
    }

    private PatientEncounterCompletionValidationDTO buildDto(
            PatientEncounter encounter,
            boolean insuranceVisit,
            ClinicalPresence presence,
            boolean canComplete,
            List<String> missing,
            List<MissingSupportiveDocumentRequirementDTO> missingRequirements,
            boolean includeDiagnostics,
            boolean vitalSignsFlag
    ) {
        List<DiagnosticCheckoutApproval> diagnosticApprovals = includeDiagnostics
                ? insuranceCheckoutDiagnosticApprovalService.findUnapproved(encounter.getId())
                : List.of();

        return new PatientEncounterCompletionValidationDTO(
                encounter.getId(),
                encounter.getCoverageType(),
                encounter.getPatientInsuranceId(),
                insuranceVisit,
                presence.chiefComplaint(),
                presence.historyOfPresentIllness(),
                presence.physicalExaminationSummary(),
                presence.primaryDiagnosis(),
                presence.assessment(),
                presence.treatmentPlan(),
                presence.medicalHistoryLegacy(),
                presence.surgicalHistory(),
                presence.socialHistoryLegacy(),
                presence.progressNotes(),
                vitalSignsFlag,
                presence.bodyMeasurements(),
                canComplete,
                List.copyOf(missing),
                diagnosticApprovals,
                List.copyOf(missingRequirements)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record ClinicalPresence(
            boolean chiefComplaint,
            boolean historyOfPresentIllness,
            boolean physicalExaminationSummary,
            boolean reviewOfSystems,
            boolean primaryDiagnosis,
            boolean assessment,
            boolean treatmentPlan,
            boolean patientProblems,
            boolean familyHistory,
            boolean hospitalizations,
            boolean currentMedication,
            boolean surgicalHistory,
            boolean socialHistoryNonCancelled,
            boolean socialHistoryLegacy,
            boolean medicalHistoryLegacy,
            boolean progressNotes,
            boolean vitalSignsActive,
            boolean bodyMeasurements,
            boolean painAssessment,
            boolean additionalMeasurements,
            boolean patientObservationsComplaints,
            boolean glasgowComaScale,
            boolean flaccNeonatesPainAssessment,
            boolean dialysisSession,
            boolean vaccination,
            boolean nephrologyKidneyAssessment,
            boolean nephrologyRenalFunction,
            boolean nephrologyTreatmentPlan,
            boolean dentalCare,
            boolean dentalProcedures,
            boolean chiefComplain,
            boolean generalAssessment,
            boolean allergies,
            boolean medicalWarnings,
            boolean consultation,
            boolean procedures,
            boolean referralRequest,
            boolean prescription,
            boolean diagnosticsOrder,
            boolean diagnosticsResult,
            boolean uccMedicationOrder,
            boolean serviceAndProducts
    ) {
    }
}
