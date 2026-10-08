package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BodyMeasurements;
import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.ClaimEncounterCopyCurrentMedication;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestReport;
import com.dazzle.asklepios.domain.ClaimEncounterCopyDiagnosticOrderTestResult;
import com.dazzle.asklepios.domain.ClaimEncounterCopyFamilyHistory;
import com.dazzle.asklepios.domain.ClaimEncounterCopyHospitalization;
import com.dazzle.asklepios.domain.ClaimEncounterCopyPatientProblem;
import com.dazzle.asklepios.domain.ClaimEncounterCopySocialHistory;
import com.dazzle.asklepios.domain.ClaimEncounterCopySurgicalHistory;
import com.dazzle.asklepios.domain.ClaimEncounterDiagnosis;
import com.dazzle.asklepios.domain.ClaimEncounterProgressNote;
import com.dazzle.asklepios.domain.DiagnosticOrder;
import com.dazzle.asklepios.domain.DiagnosticOrderTest;
import com.dazzle.asklepios.domain.EncounterAssessment;
import com.dazzle.asklepios.domain.EncounterPlan;
import com.dazzle.asklepios.domain.PatientDiagnosis;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ProgressNote;
import com.dazzle.asklepios.domain.VitalSigns;
import com.dazzle.asklepios.domain.enumeration.DiagnosticOrderTestStatus;
import com.dazzle.asklepios.domain.enumeration.DiagnosticStatus;
import com.dazzle.asklepios.domain.enumeration.PatientHistoryStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.BodyMeasurementsRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyCurrentMedicationRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyDiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyFamilyHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyHospitalizationRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyPatientProblemRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopyRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySocialHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterCopySurgicalHistoryRepository;
import com.dazzle.asklepios.repository.ClaimEncounterDiagnosisRepository;
import com.dazzle.asklepios.repository.ClaimEncounterProgressNoteRepository;
import com.dazzle.asklepios.repository.CurrentMedicationRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestReportRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestRepository;
import com.dazzle.asklepios.repository.DiagnosticOrderTestResultRepository;
import com.dazzle.asklepios.repository.EncounterAssessmentRepository;
import com.dazzle.asklepios.repository.EncounterPlanRepository;
import com.dazzle.asklepios.repository.FamilyHistoryRepository;
import com.dazzle.asklepios.repository.HospitalizationRepository;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientProblemRepository;
import com.dazzle.asklepios.repository.ProgressNoteRepository;
import com.dazzle.asklepios.repository.SocialHistoryRepository;
import com.dazzle.asklepios.repository.SurgicalHistoryRepository;
import com.dazzle.asklepios.repository.VitalSignsRepository;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyResponse;
import com.dazzle.asklepios.service.dto.billing.ClaimEncounterCopyUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class   ClaimEncounterCopyService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClaimEncounterCopyService.class);

    private final ClaimEncounterCopyRepository claimEncounterCopyRepository;
    private final EncounterPlanRepository encounterPlanRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final BodyMeasurementsRepository bodyMeasurementsRepository;
    private final PatientDiagnosisRepository patientDiagnosisRepository;
    private final ClaimEncounterDiagnosisRepository claimEncounterDiagnosisRepository;
    private final ProgressNoteRepository progressNoteRepository;
    private final ClaimEncounterProgressNoteRepository claimEncounterProgressNoteRepository;
    private final EncounterAssessmentRepository encounterAssessmentRepository;
    private final SocialHistoryRepository socialHistoryRepository;
    private final ClaimEncounterCopySocialHistoryRepository claimEncounterCopySocialHistoryRepository;
    private final SurgicalHistoryRepository surgicalHistoryRepository;
    private final ClaimEncounterCopySurgicalHistoryRepository claimEncounterCopySurgicalHistoryRepository;
    private final PatientProblemRepository patientProblemRepository;
    private final ClaimEncounterCopyPatientProblemRepository claimEncounterCopyPatientProblemRepository;
    private final FamilyHistoryRepository familyHistoryRepository;
    private final ClaimEncounterCopyFamilyHistoryRepository
            claimEncounterCopyFamilyHistoryRepository;
    private final HospitalizationRepository hospitalizationRepository;
    private final ClaimEncounterCopyHospitalizationRepository claimEncounterCopyHospitalizationRepository;
    private final CurrentMedicationRepository currentMedicationRepository;
    private final ClaimEncounterCopyCurrentMedicationRepository
            claimEncounterCopyCurrentMedicationRepository;
    private final DiagnosticOrderRepository diagnosticOrderRepository;
    private final DiagnosticOrderTestRepository diagnosticOrderTestRepository;
    private final DiagnosticOrderTestResultRepository diagnosticOrderTestResultRepository;
    private final DiagnosticOrderTestReportRepository diagnosticOrderTestReportRepository;
    private final ClaimEncounterCopyDiagnosticOrderTestResultRepository
            claimEncounterCopyDiagnosticOrderTestResultRepository;
    private final ClaimEncounterCopyDiagnosticOrderTestReportRepository
            claimEncounterCopyDiagnosticOrderTestReportRepository;
    @Transactional
    public ClaimEncounterCopy createFromEncounterIfNotExists(
            PatientEncounter encounter
    ) {
        return claimEncounterCopyRepository
                .findByEncounterId(encounter.getId())
                .orElseGet(() -> createCopy(encounter));
    }


    private ClaimEncounterCopy createCopy(PatientEncounter encounter) {
        ClaimEncounterCopy copy = new ClaimEncounterCopy();

        copy.setEncounterId(encounter.getId());
        copy.setChiefComplaint(encounter.getChiefComplaint());
        copy.setHistoryOfPresentIllness(encounter.getHistoryOfPresentIllness());
        copy.setPhysicalExamination(encounter.getPhysicalExaminationSummery());
        encounterAssessmentRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounter.getId())
                .map(EncounterAssessment::getAssessment)
                .ifPresent(copy::setAssessment);

        encounterPlanRepository
                .findTopByEncounterIdOrderByCreatedDateDesc(encounter.getId())
                .map(EncounterPlan::getTreatmentPlan)
                .ifPresent(copy::setTreatmentPlan);

        vitalSignsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(
                        encounter.getId()
                )
                .ifPresent(vitalSigns -> mapVitalSigns(copy, vitalSigns));

        bodyMeasurementsRepository
                .findFirstByEncounterIdAndIsActiveTrueOrderByCreatedDateDesc(
                        encounter.getId()
                )
                .ifPresent(bodyMeasurements -> mapBodyMeasurements(copy, bodyMeasurements));

        ClaimEncounterCopy savedCopy = claimEncounterCopyRepository.save(copy);

        copyDiagnoses(encounter.getId(), savedCopy.getId());
        copyProgressNotes(encounter.getId(), savedCopy.getId());
        copySocialHistories(
                encounter.getPatient().getId(),
                savedCopy.getId()
        );
        copySurgicalHistories(
                encounter.getPatient().getId(),
                savedCopy.getId()
        );
        copyPatientProblems(
                encounter.getPatient().getId(),
                savedCopy.getId()
        );
        copyFamilyHistories(
                encounter.getPatient().getId(),
                savedCopy.getId()
        );
        copyHospitalizations(
                encounter.getPatient().getId(),
                savedCopy.getId()
        );
        copyCurrentMedications( encounter.getPatient().getId(), savedCopy.getId());
        copyDiagnosticOrderTestResults(encounter.getId(), savedCopy.getId());
        copyDiagnosticOrderTestReports(encounter.getId(), savedCopy.getId());

        return savedCopy;
    }

    private void mapVitalSigns(
            ClaimEncounterCopy copy,
            VitalSigns vitalSigns
    ) {
        copy.setPulse(vitalSigns.getHeartRate());
        copy.setTemperature(vitalSigns.getTemperature());
        copy.setRespiratoryRate(vitalSigns.getRespiratoryRate());
        copy.setOxygenSaturation(vitalSigns.getOxygenSaturation());
        copy.setBloodPressureSystolic(vitalSigns.getBloodPressureSystolic());
        copy.setBloodPressureDiastolic(vitalSigns.getBloodPressureDiastolic());
    }

    private void mapBodyMeasurements(
            ClaimEncounterCopy copy,
            BodyMeasurements bodyMeasurements
    ) {
        copy.setHeight(bodyMeasurements.getHeight());
        copy.setWeight(bodyMeasurements.getWeight());
    }

    @Transactional(readOnly = true)
    public ClaimEncounterCopyResponse findByEncounterId(Long encounterId) {
        LOG.debug(
                "REST request claim encounter copy encounterId={}",
                encounterId
        );

        return claimEncounterCopyRepository
                .findByEncounterId(encounterId)
                .map(this::mapToResponse)
                .orElse(null);
    }

    private ClaimEncounterCopyResponse mapToResponse(
            ClaimEncounterCopy copy
    ) {
        return new ClaimEncounterCopyResponse(
                copy.getId(),
                copy.getEncounterId(),
                copy.getChiefComplaint(),
                copy.getHistoryOfPresentIllness(),
                copy.getPhysicalExamination(),
                copy.getAssessment(),
                copy.getTreatmentPlan(),
                copy.getPulse(),
                copy.getTemperature(),
                copy.getRespiratoryRate(),
                copy.getOxygenSaturation(),
                copy.getBloodPressureSystolic(),
                copy.getBloodPressureDiastolic(),
                copy.getHeight(),
                copy.getWeight()
        );
    }
    @Transactional
    public ClaimEncounterCopyResponse update(
            Long encounterId,
            ClaimEncounterCopyUpdateRequest request
    ) {
        ClaimEncounterCopy copy =
                claimEncounterCopyRepository
                        .findByEncounterId(encounterId)
                        .orElse(null);

        if (copy == null) {
            return null;
        }

        copy.setChiefComplaint(request.chiefComplaint());
        copy.setHistoryOfPresentIllness(request.historyOfPresentIllness());
        copy.setPhysicalExamination(request.physicalExamination());
        copy.setAssessment(request.assessment());
        copy.setTreatmentPlan(request.treatmentPlan());
        copy.setPulse(request.pulse());
        copy.setTemperature(request.temperature());
        copy.setRespiratoryRate(request.respiratoryRate());
        copy.setOxygenSaturation(request.oxygenSaturation());
        copy.setBloodPressureSystolic(request.bloodPressureSystolic());
        copy.setBloodPressureDiastolic(request.bloodPressureDiastolic());
        copy.setHeight(request.height());
        copy.setWeight(request.weight());

        return mapToResponse(claimEncounterCopyRepository.save(copy));
    }

    private void copyDiagnoses(Long encounterId, Long claimEncounterCopyId) {
        List<PatientDiagnosis> diagnoses =
                patientDiagnosisRepository.findByEncounterId(encounterId);

        List<ClaimEncounterDiagnosis> claimDiagnoses = diagnoses.stream()
                .map(diagnosis -> ClaimEncounterDiagnosis.builder()
                        .claimEncounterCopyId(claimEncounterCopyId)
                        .encounterId(encounterId)
                        .diagnosisId(diagnosis.getDiagnosisId())
                        .type(diagnosis.getType())
                        .suspected(diagnosis.getSuspected())
                        .major(diagnosis.getMajor())
                        .build())
                .toList();

        claimEncounterDiagnosisRepository.saveAll(claimDiagnoses);
    }

    private void copyProgressNotes(
            Long encounterId,
            Long claimEncounterCopyId
    ) {
        List<ProgressNote> progressNotes =
                progressNoteRepository.findByEncounterIdAndCancelledDateIsNull(
                        encounterId
                );

        List<ClaimEncounterProgressNote> claimProgressNotes =
                progressNotes.stream()
                        .map(progressNote -> ClaimEncounterProgressNote.builder()
                                .claimEncounterCopyId(claimEncounterCopyId)
                                .encounterId(encounterId)
                                .progressNoteId(progressNote.getId())
                                .noteText(progressNote.getNoteText())
                                .build())
                        .toList();

        claimEncounterProgressNoteRepository.saveAll(claimProgressNotes);
    }

    private void copySocialHistories(Long patientId, Long claimEncounterCopyId) {
        socialHistoryRepository
                .findAllByPatientIdAndStatus(
                        patientId,
                        PatientHistoryStatus.ACTIVE
                )
                .forEach(socialHistory -> {

                    ClaimEncounterCopySocialHistory copy =
                            new ClaimEncounterCopySocialHistory();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setSocialHistoryId(socialHistory.getId());

                    copy.setIsCurrentSmoker(socialHistory.getIsCurrentSmoker());
                    copy.setSmokeStartDate(socialHistory.getSmokeStartDate());
                    copy.setCigaretteAmount(socialHistory.getCigaretteAmount());
                    copy.setCigaretteType(socialHistory.getCigaretteType());

                    copy.setIsPreviousSmoker(socialHistory.getIsPreviousSmoker());
                    copy.setSmokeQuitDate(socialHistory.getSmokeQuitDate());

                    copy.setExposureToSecondHandSmoke(
                            socialHistory.getExposureToSecondHandSmoke()
                    );

                    copy.setAlcoholConsumption(
                            socialHistory.getAlcoholConsumption()
                    );
                    copy.setTypeOfAlcohol(socialHistory.getTypeOfAlcohol());
                    copy.setAlcoholSinceWhen(socialHistory.getAlcoholSinceWhen());

                    copy.setSubstanceUse(socialHistory.getSubstanceUse());
                    copy.setRoute(socialHistory.getRoute());
                    copy.setFrequency(socialHistory.getFrequency());

                    copy.setPhysicalLimitation(
                            socialHistory.getPhysicalLimitation()
                    );
                    copy.setDiagnosedEatingDisorders(
                            socialHistory.getDiagnosedEatingDisorders()
                    );

                    copy.setPatientIsFree(socialHistory.getPatientIsFree());
                    copy.setFreeText(socialHistory.getFreeText());

                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopySocialHistoryRepository.save(copy);
                });
    }
    private void copySurgicalHistories(
            Long patientId,
            Long claimEncounterCopyId
    ) {
        surgicalHistoryRepository
                .findAllByPatientIdAndStatus(
                        patientId,
                        PatientHistoryStatus.ACTIVE
                )
                .forEach(surgicalHistory -> {

                    ClaimEncounterCopySurgicalHistory copy =
                            new ClaimEncounterCopySurgicalHistory();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setSurgicalHistoryId(surgicalHistory.getId());

                    copy.setSurgery(surgicalHistory.getSurgery());
                    copy.setDateOfSurgery(
                            surgicalHistory.getDateOfSurgery()
                    );
                    copy.setFacility(surgicalHistory.getFacility());
                    copy.setAnesthesiaType(
                            surgicalHistory.getAnesthesiaType()
                    );

                    copy.setComplications(
                            surgicalHistory.getComplications()
                    );
                    copy.setAdverseReactionsToAnesthesia(
                            surgicalHistory.getAdverseReactionsToAnesthesia()
                    );

                    copy.setHasImplantsOrDevices(
                            surgicalHistory.getHasImplantsOrDevices()
                    );
                    copy.setImplantsOrDevicesDescription(
                            surgicalHistory.getImplantsOrDevicesDescription()
                    );

                    copy.setPatientIsFree(
                            surgicalHistory.getPatientIsFree()
                    );
                    copy.setFreeText(
                            surgicalHistory.getFreeText()
                    );

                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopySurgicalHistoryRepository.save(copy);
                });
    }
    private void copyPatientProblems(
            Long patientId,
            Long claimEncounterCopyId
    ) {
        patientProblemRepository
                .findAllByPatientIdAndStatus(
                        patientId,
                        PatientHistoryStatus.ACTIVE
                )
                .forEach(patientProblem -> {

                    ClaimEncounterCopyPatientProblem copy =
                            new ClaimEncounterCopyPatientProblem();

                    copy.setClaimEncounterCopyId(
                            claimEncounterCopyId
                    );

                    copy.setPatientProblemId(
                            patientProblem.getId()
                    );

                    copy.setCondition(
                            patientProblem.getCondition()
                    );

                    copy.setDateOfDiagnosis(
                            patientProblem.getDateOfDiagnosis()
                    );

                    copy.setConditionStatus(
                            patientProblem.getConditionStatus()
                    );

                    copy.setType(
                            patientProblem.getType()
                    );

                    copy.setDateOfResolution(
                            patientProblem.getDateOfResolution()
                    );

                    copy.setByPatient(
                            patientProblem.getByPatient()
                    );

                    copy.setSourceOfInformation(
                            patientProblem.getSourceOfInformation()
                    );

                    copy.setPatientIsFree(
                            patientProblem.getPatientIsFree()
                    );

                    copy.setFreeText(
                            patientProblem.getFreeText()
                    );

                    copy.setStatus(
                            PatientHistoryStatus.ACTIVE
                    );

                    claimEncounterCopyPatientProblemRepository.save(
                            copy
                    );
                });
    }
    private void copyFamilyHistories(
            Long patientId,
            Long claimEncounterCopyId
    ) {
        familyHistoryRepository
                .findAllByPatientIdAndStatus(
                        patientId,
                        PatientHistoryStatus.ACTIVE
                )
                .forEach(familyHistory -> {

                    ClaimEncounterCopyFamilyHistory copy =
                            new ClaimEncounterCopyFamilyHistory();

                    copy.setClaimEncounterCopyId(
                            claimEncounterCopyId
                    );

                    copy.setFamilyHistoryId(
                            familyHistory.getId()
                    );

                    copy.setCondition(
                            familyHistory.getCondition()
                    );

                    copy.setRelation(
                            familyHistory.getRelation()
                    );

                    copy.setInheritedDiseases(
                            familyHistory.getInheritedDiseases()
                    );

                    copy.setPatientIsFree(
                            familyHistory.getPatientIsFree()
                    );

                    copy.setFreeText(
                            familyHistory.getFreeText()
                    );

                    copy.setStatus(
                            PatientHistoryStatus.ACTIVE
                    );

                    claimEncounterCopyFamilyHistoryRepository.save(
                            copy
                    );
                });
    }
    private void copyHospitalizations(
            Long patientId,
            Long claimEncounterCopyId
    ) {
        hospitalizationRepository
                .findAllByPatientIdAndStatusNot(
                        patientId,
                        PatientHistoryStatus.CANCELLED
                )
                .forEach(hospitalization -> {

                    ClaimEncounterCopyHospitalization copy =
                            new ClaimEncounterCopyHospitalization();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setHospitalizationId(hospitalization.getId());

                    copy.setFacility(hospitalization.getFacility());
                    copy.setReason(hospitalization.getReason());
                    copy.setAdmissionType(hospitalization.getAdmissionType());
                    copy.setDateOfAdmission(
                            hospitalization.getDateOfAdmission()
                    );
                    copy.setLengthOfStayDays(
                            hospitalization.getLengthOfStayDays()
                    );
                    copy.setOutcomes(hospitalization.getOutcomes());
                    copy.setMedicalInterventionsPerformed(
                            hospitalization.getMedicalInterventionsPerformed()
                    );

                    copy.setPatientIsFree(
                            hospitalization.getPatientIsFree()
                    );
                    copy.setFreeText(
                            hospitalization.getFreeText()
                    );

                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopyHospitalizationRepository.save(copy);
                });

    }


    private void copyCurrentMedications(
            Long patientId,
            Long claimEncounterCopyId
    ) {
        currentMedicationRepository
                .findAllByPatientIdAndStatusNot(
                        patientId,
                        PatientHistoryStatus.CANCELLED
                )
                .forEach(currentMedication -> {
                    ClaimEncounterCopyCurrentMedication copy =
                            new ClaimEncounterCopyCurrentMedication();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setCurrentMedicationId(currentMedication.getId());
                    copy.setActiveIngredientId(
                            currentMedication.getActiveIngredientId()
                    );
                    copy.setDosage(currentMedication.getDosage());
                    copy.setUnit(currentMedication.getUnit());
                    copy.setFrequency(currentMedication.getFrequency());
                    copy.setStartDate(currentMedication.getStartDate());
                    copy.setPatientIsFree(
                            currentMedication.getPatientIsFree()
                    );
                    copy.setFreeText(currentMedication.getFreeText());
                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopyCurrentMedicationRepository.save(copy);
                });
    }

    private void copyDiagnosticOrderTestResults(
            Long encounterId,
            Long claimEncounterCopyId
    ) {
        List<Long> orderIds = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED)
                .stream()
                .map(DiagnosticOrder::getId)
                .toList();

        if (orderIds.isEmpty()) {
            return;
        }

        List<Long> labTestIds = diagnosticOrderTestRepository
                .findByOrderIdInAndOrderTypeAndStatusNot(
                        orderIds,
                        TestType.LABORATORY,
                        DiagnosticOrderTestStatus.CANCELLED
                )
                .stream()
                .map(DiagnosticOrderTest::getId)
                .toList();

        if (labTestIds.isEmpty()) {
            return;
        }

        diagnosticOrderTestResultRepository.findByOrderTestIdIn(labTestIds)
                .stream()
                .filter(result -> result.getProcessingStatus() != DiagnosticStatus.REJECTED)
                .forEach(result -> {
                    ClaimEncounterCopyDiagnosticOrderTestResult copy =
                            new ClaimEncounterCopyDiagnosticOrderTestResult();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setDiagnosticOrderTestResultId(result.getId());
                    copy.setOrderTestId(result.getOrderTestId());
                    copy.setProfileTestId(result.getProfileTestId());
                    copy.setResultValueNumber(result.getResultValueNumber());
                    copy.setResultValueText(result.getResultValueText());
                    copy.setMarker(result.getMarker());
                    copy.setNormalRangeValue(result.getNormalRangeValue());
                    copy.setResultTypeAtEntry(result.getResultTypeAtEntry());
                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopyDiagnosticOrderTestResultRepository.save(copy);
                });
    }

    private void copyDiagnosticOrderTestReports(
            Long encounterId,
            Long claimEncounterCopyId
    ) {
        List<Long> orderIds = diagnosticOrderRepository
                .findByEncounterIdAndStatusNot(encounterId, DiagnosticStatus.CANCELLED)
                .stream()
                .map(DiagnosticOrder::getId)
                .toList();

        if (orderIds.isEmpty()) {
            return;
        }

        List<Long> radTestIds = diagnosticOrderTestRepository
                .findByOrderIdInAndOrderTypeAndStatusNot(
                        orderIds,
                        TestType.RADIOLOGY,
                        DiagnosticOrderTestStatus.CANCELLED
                )
                .stream()
                .map(DiagnosticOrderTest::getId)
                .toList();

        if (radTestIds.isEmpty()) {
            return;
        }

        diagnosticOrderTestReportRepository.findByOrderTestIdIn(radTestIds)
                .stream()
                .filter(report -> report.getProcessingStatus() != DiagnosticStatus.REJECTED)
                .forEach(report -> {
                    ClaimEncounterCopyDiagnosticOrderTestReport copy =
                            new ClaimEncounterCopyDiagnosticOrderTestReport();

                    copy.setClaimEncounterCopyId(claimEncounterCopyId);
                    copy.setDiagnosticOrderTestReportId(report.getId());
                    copy.setOrderTestId(report.getOrderTestId());
                    copy.setReport(report.getReport());
                    copy.setRadiologistInformation(report.getRadiologistInformation());
                    copy.setCriticalFindings(report.getCriticalFindings());
                    copy.setRadiologistComments(report.getRadiologistComments());
                    copy.setSeverity(report.getSeverity());
                    copy.setStatus(PatientHistoryStatus.ACTIVE);

                    claimEncounterCopyDiagnosticOrderTestReportRepository.save(copy);
                });
    }
}