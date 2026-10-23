package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import com.dazzle.asklepios.domain.EncounterAmbient;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.AmbientConsentStatus;
import com.dazzle.asklepios.repository.EncounterAmbientRepository;
import com.dazzle.asklepios.repository.EncounterAmbientRepository.EncounterAmbientDetail;
import com.dazzle.asklepios.repository.EncounterAmbientRepository.EncounterAmbientSummary;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.service.dto.encounterAmbient.EncounterAmbientCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.encounter.EncounterAmbientVM;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class EncounterAmbientService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAmbientService.class);
    private static final String ENTITY_NAME = "encounterAmbient";
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

    private final EncounterAmbientRepository encounterAmbientRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final ReopenSessionAuditContext reopenSessionAuditContext;
    private final AttachmentStorageService attachmentStorageService;
    private final AttachmentProperties attachmentProperties;
    private final ObjectMapper objectMapper;

    public EncounterAmbientService(
        EncounterAmbientRepository encounterAmbientRepository,
        PatientRepository patientRepository,
        PatientEncounterRepository patientEncounterRepository,
        ReopenSessionAuditContext reopenSessionAuditContext,
        AttachmentStorageService attachmentStorageService,
        AttachmentProperties attachmentProperties,
        ObjectMapper objectMapper
    ) {
        this.encounterAmbientRepository = encounterAmbientRepository;
        this.patientRepository = patientRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.reopenSessionAuditContext = reopenSessionAuditContext;
        this.attachmentStorageService = attachmentStorageService;
        this.attachmentProperties = attachmentProperties;
        this.objectMapper = objectMapper;
    }

    public record RecordingFile(String contentType, String fileName, byte[] recording) {}

    public EncounterAmbientVM create(EncounterAmbientCreateDTO dto, MultipartFile audio) {
        LOG.info("[CREATE] EncounterAmbient patientId={} encounterId={}", dto.patientId(), dto.encounterId());

        Patient patient = patientRepository
            .findById(dto.patientId())
            .orElseThrow(() -> new NotFoundAlertException("Patient not found", ENTITY_NAME, "patient.notfound"));
        PatientEncounter encounter = patientEncounterRepository
            .findById(dto.encounterId())
            .orElseThrow(() -> new NotFoundAlertException("Encounter not found with id " + dto.encounterId(), ENTITY_NAME, "encounter.notfound"));

        if (encounter.getPatient() == null || !patient.getId().equals(encounter.getPatient().getId())) {
            throw new BadRequestAlertException("Patient does not match the encounter", ENTITY_NAME, "patient.mismatch");
        }

        if (dto.consentStatus() == AmbientConsentStatus.DECLINED && audio != null && !audio.isEmpty()) {
            throw new BadRequestAlertException("A declined consent cannot include a recording", ENTITY_NAME, "recording.notallowed");
        }

        Instant now = Instant.now();
        String draftJson = draftToJson(dto.draft());
        StoredRecording stored = storeRecording(encounter.getId(), audio, now);
        reopenSessionAuditContext.applyOpenSession(encounter.getId());

        try {
            EncounterAmbient saved = encounterAmbientRepository.saveAndFlush(
                EncounterAmbient.builder()
                    .patient(patient)
                    .encounter(encounter)
                    .consentStatus(dto.consentStatus())
                    .consentMethod(dto.consentMethod())
                    .microphoneSource(dto.microphoneSource())
                    .consentAt(dto.consentAt() != null ? dto.consentAt() : now)
                    .recordingStartedAt(dto.recordingStartedAt())
                    .recordingStoppedAt(dto.recordingStoppedAt())
                    .recordingDurationMs(dto.recordingDurationMs())
                    .recordingContentType(stored == null ? null : stored.contentType())
                    .recordingFileName(stored == null ? null : stored.fileName())
                    .recordingByteSize(stored == null ? null : stored.size())
                    .spaceKey(stored == null ? null : stored.spaceKey())
                    .draftJson(draftJson)
                    .draftCreatedAt(draftJson == null ? null : now)
                    .build()
            );
            return toVm(saved.getId());
        } catch (RuntimeException exception) {
            if (stored != null) {
                try {
                    attachmentStorageService.delete(stored.spaceKey());
                } catch (RuntimeException cleanupException) {
                    LOG.warn("Could not remove ambient recording {} after a failed save", stored.spaceKey());
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<EncounterAmbientVM> findByEncounter(Long encounterId) {
        if (!patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException("Encounter not found with id " + encounterId, ENTITY_NAME, "encounter.notfound");
        }
        return encounterAmbientRepository.findByEncounter_IdOrderByCreatedDateDesc(encounterId).stream().map(this::toVm).toList();
    }

    @Transactional(readOnly = true)
    public EncounterAmbientVM findOne(Long id) {
        return toVm(id);
    }

    public EncounterAmbientVM updateDraft(Long id, JsonNode draft) {
        EncounterAmbient ambient = encounterAmbientRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundAlertException("Ambient session not found", ENTITY_NAME, "id.notfound"));
        if (draft == null || draft.isNull()) {
            throw new BadRequestAlertException("Draft JSON is required", ENTITY_NAME, "draft.required");
        }

        reopenSessionAuditContext.applyOpenSession(ambient.getEncounter().getId());
        ambient.setDraftJson(draft.toString());
        ambient.setDraftCreatedAt(Instant.now());
        encounterAmbientRepository.saveAndFlush(ambient);
        return toVm(id);
    }

    @Transactional(readOnly = true)
    public RecordingFile getRecording(Long id) {
        EncounterAmbient ambient = encounterAmbientRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundAlertException("Ambient session not found", ENTITY_NAME, "id.notfound"));
        if (ambient.getSpaceKey() == null || ambient.getSpaceKey().isBlank()) {
            throw new NotFoundAlertException("Recording not found", ENTITY_NAME, "recording.notfound");
        }
        byte[] recording = attachmentStorageService.getBytes(ambient.getSpaceKey());
        if (recording == null || recording.length == 0) {
            throw new NotFoundAlertException("Recording not found", ENTITY_NAME, "recording.notfound");
        }
        return new RecordingFile(ambient.getRecordingContentType(), ambient.getRecordingFileName(), recording);
    }

    private EncounterAmbientVM toVm(Long id) {
        EncounterAmbientDetail detail = encounterAmbientRepository
            .findById(id, EncounterAmbientDetail.class)
            .orElseThrow(() -> new NotFoundAlertException("Ambient session not found", ENTITY_NAME, "id.notfound"));
        return toVm(detail, detail.getDraftJson());
    }

    private EncounterAmbientVM toVm(EncounterAmbientSummary summary) {
        return toVm(summary, null);
    }

    private EncounterAmbientVM toVm(EncounterAmbientSummary summary, String draftJson) {
        Long byteSize = summary.getRecordingByteSize();
        return new EncounterAmbientVM(
            summary.getId(),
            summary.getEncounter().getId(),
            summary.getPatient().getId(),
            summary.getConsentStatus(),
            summary.getConsentMethod(),
            summary.getMicrophoneSource(),
            summary.getConsentAt(),
            summary.getRecordingStartedAt(),
            summary.getRecordingStoppedAt(),
            summary.getRecordingDurationMs(),
            summary.getRecordingContentType(),
            summary.getRecordingFileName(),
            byteSize,
            byteSize != null && byteSize > 0,
            parseStoredDraft(draftJson),
            summary.getDraftCreatedAt(),
            summary.getCreatedDate(),
            summary.getLastModifiedDate()
        );
    }

    private String draftToJson(JsonNode draft) {
        if (draft == null || draft.isNull() || draft.isMissingNode()) {
            return null;
        }
        return draft.toString();
    }

    private JsonNode parseStoredDraft(String draftJson) {
        if (draftJson == null || draftJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(draftJson);
        } catch (JsonProcessingException exception) {
            LOG.warn("Stored ambient draft is not valid JSON: {}", exception.getMessage());
            return null;
        }
    }

    private StoredRecording storeRecording(Long encounterId, MultipartFile audio, Instant now) {
        if (audio == null || audio.isEmpty()) {
            return null;
        }
        String contentType = baseContentType(audio.getContentType());
        if (contentType == null) {
            contentType = contentTypeForFileName(audio.getOriginalFilename());
        }
        String fileName = sanitizeFilename(audio.getOriginalFilename(), contentType);
        if (contentType == null) {
            contentType = contentTypeForFileName(fileName);
        }
        if (contentType == null) {
            contentType = "audio/wav";
            fileName = "ambient-recording.wav";
        }
        long size = audio.getSize();
        if (attachmentProperties.getAllowed() != null
            && !attachmentProperties.getAllowed().isEmpty()
            && !attachmentProperties.getAllowed().contains(contentType)) {
            throw new BadRequestAlertException("Unsupported file type", ENTITY_NAME, "unsupported_type");
        }
        if (attachmentProperties.getMaxBytes() > 0 && size > attachmentProperties.getMaxBytes()) {
            throw new BadRequestAlertException("File too large", ENTITY_NAME, "too_large");
        }
        String spaceKey = "encounter-ambient/"
            + encounterId
            + "/"
            + YEAR.format(now)
            + "/"
            + MONTH.format(now)
            + "/"
            + UUID.randomUUID()
            + "_"
            + fileName;
        try {
            attachmentStorageService.putBytes(spaceKey, contentType, audio.getBytes());
        } catch (IOException exception) {
            throw new BadRequestAlertException("Recording could not be read", ENTITY_NAME, "recording.unreadable");
        }
        return new StoredRecording(spaceKey, contentType, fileName, size);
    }

    private static String baseContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase();
        return base.isEmpty() ? null : base;
    }

    private static String contentTypeForFileName(String name) {
        if (name == null) {
            return null;
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".mp3")) {
            return "audio/mpeg";
        }
        if (lower.endsWith(".m4a") || lower.endsWith(".mp4")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".webm")) {
            return "audio/webm";
        }
        return null;
    }

    private static String defaultFileName(String contentType) {
        if ("audio/mpeg".equals(contentType)) {
            return "ambient-recording.mp3";
        }
        if ("audio/mp4".equals(contentType) || "audio/m4a".equals(contentType) || "audio/x-m4a".equals(contentType)) {
            return "ambient-recording.m4a";
        }
        if ("audio/ogg".equals(contentType)) {
            return "ambient-recording.ogg";
        }
        if ("audio/webm".equals(contentType)) {
            return "ambient-recording.webm";
        }
        return "ambient-recording.wav";
    }

    private static String sanitizeFilename(String name, String contentType) {
        if (name == null || name.isBlank()) {
            return defaultFileName(contentType);
        }
        String sanitized = name.replaceAll("[^\\w.\\- ]", "_");
        return sanitized.isBlank() ? defaultFileName(contentType) : sanitized;
    }

    private record StoredRecording(String spaceKey, String contentType, String fileName, long size) {}
}
