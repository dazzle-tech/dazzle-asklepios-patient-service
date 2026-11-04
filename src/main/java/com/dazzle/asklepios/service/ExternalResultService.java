package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.EncounterAttachmentClient;
import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.domain.ExternalLabResult;
import com.dazzle.asklepios.domain.ExternalRadiologyReport;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import com.dazzle.asklepios.repository.ExternalLabResultRepository;
import com.dazzle.asklepios.repository.ExternalRadiologyReportRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.externalresult.ExternalLabResultSaveDTO;
import com.dazzle.asklepios.service.dto.externalresult.ExternalRadiologyReportSaveDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;


@Service
@Transactional
@RequiredArgsConstructor
public class ExternalResultService {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalResultService.class);
    private static final String ENTITY_NAME = "externalResult";
    private static final String ATTACHMENT_SOURCE = "SCANNED_DOCUMENT_ATTACHMENT";
    private static final String ATTACHMENT_DETAILS = "Uploaded by AI - Medical Document Processor";
    private static final String ATTACHMENT_TYPE_LOV = "ATTACH_TYPE";
    private static final String MEDICAL_FILE_CODE = "ATAC_TYP_MED";

    private final ExternalLabResultRepository externalLabResultRepository;
    private final ExternalRadiologyReportRepository externalRadiologyReportRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ApLovValueRepository apLovValueRepository;
    private final EncounterAttachmentClient encounterAttachmentClient;

    public List<ExternalLabResult> saveLabResults(ExternalLabResultSaveDTO dto, MultipartFile file) {
        Objects.requireNonNull(dto, "dto must not be null");
        validatePatientAndEncounter(dto.patientId(), dto.encounterId());
        LOG.debug("Request to save {} ExternalLabResult rows patientId={} encounterId={}",
                dto.results().size(), dto.patientId(), dto.encounterId());

        boolean translated = Boolean.TRUE.equals(dto.translated());
        List<ExternalLabResult> rows = dto.results().stream()
                .map(item -> ExternalLabResult.builder()
                        .patientId(dto.patientId())
                        .encounterId(dto.encounterId())
                        .testName(item.testName().trim())
                        .resultDate(item.resultDate())
                        .resultValue(item.resultValue())
                        .unit(item.unit())
                        .normalRangeValue(item.normalRangeValue())
                        .marker(item.marker())
                        .physicianName(item.physicianName())
                        .rawExtractedText(dto.rawExtractedText())
                        .detectedLanguage(dto.detectedLanguage())
                        .translated(translated)
                        .build())
                .toList();

        List<ExternalLabResult> saved = externalLabResultRepository.saveAll(rows);

        // Uploaded last: if it fails, the transaction rolls the rows back
        Long attachmentId = uploadAttachment(dto.encounterId(), file);
        saved.forEach(row -> row.setAttachmentId(attachmentId));
        LOG.info("Saved {} ExternalLabResult rows patientId={} encounterId={}",
                saved.size(), dto.patientId(), dto.encounterId());
        return saved;
    }


    public List<ExternalRadiologyReport> saveRadiologyReports(ExternalRadiologyReportSaveDTO dto, MultipartFile file) {
        Objects.requireNonNull(dto, "dto must not be null");
        validatePatientAndEncounter(dto.patientId(), dto.encounterId());
        LOG.debug("Request to save {} ExternalRadiologyReport rows patientId={} encounterId={}",
                dto.reports().size(), dto.patientId(), dto.encounterId());

        boolean translated = Boolean.TRUE.equals(dto.translated());
        List<ExternalRadiologyReport> rows = dto.reports().stream()
                .map(item -> ExternalRadiologyReport.builder()
                        .patientId(dto.patientId())
                        .encounterId(dto.encounterId())
                        .testName(item.testName())
                        .reportDate(item.reportDate())
                        .report(item.report())
                        .severity(item.severity())
                        .radiologistInformation(item.radiologistInformation())
                        .criticalFindings(item.criticalFindings())
                        .radiologistComments(item.radiologistComments())
                        .physicianName(item.physicianName())
                        .rawExtractedText(dto.rawExtractedText())
                        .detectedLanguage(dto.detectedLanguage())
                        .translated(translated)
                        .build())
                .toList();

        List<ExternalRadiologyReport> saved = externalRadiologyReportRepository.saveAll(rows);

        // Uploaded last: if it fails, the transaction rolls the rows back
        Long attachmentId = uploadAttachment(dto.encounterId(), file);
        saved.forEach(row -> row.setAttachmentId(attachmentId));
        LOG.info("Saved {} ExternalRadiologyReport rows patientId={} encounterId={}",
                saved.size(), dto.patientId(), dto.encounterId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ExternalLabResult> getLabResultsByPatient(Long patientId) {
        Objects.requireNonNull(patientId, "patientId must not be null");
        return externalLabResultRepository.findByPatientIdOrderByResultDateDescIdAsc(patientId);
    }

    @Transactional(readOnly = true)
    public List<ExternalLabResult> getLabResultsByEncounter(Long encounterId) {
        Objects.requireNonNull(encounterId, "encounterId must not be null");
        return externalLabResultRepository.findByEncounterIdOrderByResultDateDescIdAsc(encounterId);
    }

    @Transactional(readOnly = true)
    public List<ExternalRadiologyReport> getRadiologyReportsByPatient(Long patientId) {
        Objects.requireNonNull(patientId, "patientId must not be null");
        return externalRadiologyReportRepository.findByPatientIdOrderByReportDateDescIdAsc(patientId);
    }

    @Transactional(readOnly = true)
    public List<ExternalRadiologyReport> getRadiologyReportsByEncounter(Long encounterId) {
        Objects.requireNonNull(encounterId, "encounterId must not be null");
        return externalRadiologyReportRepository.findByEncounterIdOrderByReportDateDescIdAsc(encounterId);
    }

    // The file is stored by the setup service, which owns encounter attachments
    private Long uploadAttachment(Long encounterId, MultipartFile file) {
        if (encounterId == null || file == null || file.isEmpty()) {
            return null;
        }
        try {
            return encounterAttachmentClient
                    .upload(encounterId, file, medicalFileType(), ATTACHMENT_DETAILS, ATTACHMENT_SOURCE, "0")
                    .id();
        } catch (FeignException e) {
            LOG.warn("Failed to upload external result file encounterId={} status={}", encounterId, e.status());
            throw new BadRequestAlertException("Attachment upload failed", ENTITY_NAME, "The document could not be saved as an attachment");
        }
    }

    // The LOV key differs per environment, so it is looked up by its fixed code
    private String medicalFileType() {
        return apLovValueRepository.findByLovCode(ATTACHMENT_TYPE_LOV).stream()
                .filter(lov -> MEDICAL_FILE_CODE.equals(lov.getValueCode()))
                .map(ApLovValue::getKey)
                .findFirst()
                .orElse("");
    }

    // The encounter is optional, but when given it must belong to the same patient
    private void validatePatientAndEncounter(Long patientId, Long encounterId) {
        if (!patientRepository.existsById(patientId)) {
            throw new BadRequestAlertException("Patient not found", ENTITY_NAME, "patient.notfound");
        }
        if (encounterId == null) {
            return;
        }
        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException("Encounter not found", ENTITY_NAME, "encounter.notfound"));
        if (encounter.getPatient() == null || !patientId.equals(encounter.getPatient().getId())) {
            throw new BadRequestAlertException(
                    "Encounter does not belong to the patient", ENTITY_NAME, "encounter.patient.mismatch");
        }
    }
}
