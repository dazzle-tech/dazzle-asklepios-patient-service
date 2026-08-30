package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import com.dazzle.asklepios.domain.DocumentAssignment;
import com.dazzle.asklepios.domain.DocumentDefinition;
import com.dazzle.asklepios.domain.DocumentVersion;
import com.dazzle.asklepios.domain.enumeration.DocumentRequirementStatus;
import com.dazzle.asklepios.domain.enumeration.DocumentStatus;
import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import com.dazzle.asklepios.repository.DocumentAssignmentRepository;
import com.dazzle.asklepios.repository.DocumentDefinitionRepository;
import com.dazzle.asklepios.repository.DocumentVersionRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.documentAssignment.DocumentAssignmentDTO;
import com.dazzle.asklepios.service.dto.documentDefinition.DocumentDefinitionDTO;
import com.dazzle.asklepios.service.dto.documentVersion.DocumentVersionDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.documentAssignment.DocumentAssignmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.documentAssignment.DocumentRequirementVM;
import com.dazzle.asklepios.web.rest.vm.documentDefinition.DocumentDefinitionResponseVM;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentTemplateDownloadVM;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentVersionResponseVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class DocumentManagementService {

    private final DocumentDefinitionRepository documentDefinitionRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentAssignmentRepository assignmentRepository;

    private final AttachmentStorageService storage;
    private final AttachmentProperties props;

    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MM = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);


    // ============================================================
    // document DEFINITION
    // ============================================================

    @Transactional(readOnly = true)
    public Page<DocumentDefinitionResponseVM> searchDocuments(String search, DocumentStatus status, Pageable pageable) {
        Specification<DocumentDefinition> specification = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String searchValue = "%" + search.toLowerCase() + "%";

            specification = specification.and(
                    (root, query, cb) -> cb.or(
                            cb.like(
                                    cb.lower(root.get("code")),
                                    searchValue
                            ),
                            cb.like(
                                    cb.lower(root.get("name")),
                                    searchValue
                            )
                    )
            );
        }

        if (status != null) {
            specification = specification.and(
                    (root, query, cb) ->
                            cb.equal(root.get("status"), status)
            );
        }

        return documentDefinitionRepository
                .findAll(specification, pageable)
                .map(entity -> {
                    DocumentVersion activeVersion = entity.getVersions()
                            .stream()
                            .filter(version ->
                                    version.getStatus() == DocumentStatus.ACTIVE
                            )
                            .findFirst()
                            .orElse(null);

                    return toDefinitionResponse(entity, activeVersion);
                });
    }

    @Transactional
    public DocumentDefinitionResponseVM createDocument(DocumentDefinitionDTO dto) {

        validateDocumentCode(dto.code());

        DocumentDefinition entity = new DocumentDefinition();

        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setCategory(dto.category());
        entity.setStatus(dto.status() != null ? dto.status() : DocumentStatus.DRAFT);

        DocumentDefinition saved = documentDefinitionRepository.save(entity);

        return toDefinitionResponse(saved);
    }

    @Transactional(readOnly = true)
    public DocumentDefinitionResponseVM getDocument(Long id) {

        DocumentDefinition document = getdocumentDefinition(id);

        DocumentVersion activeVersion = documentVersionRepository.findByDocumentDefinition_IdIsAndStatusIs(id, DocumentStatus.ACTIVE)
                .orElse(null);

        return toDefinitionResponse(document, activeVersion);
    }

    @Transactional
    public DocumentDefinitionResponseVM updateDocument(Long id, DocumentDefinitionDTO dto) {

        DocumentDefinition document = getdocumentDefinition(id);

        if (dto.name() != null) {
            document.setName(dto.name());
        }

        if (dto.description() != null) {
            document.setDescription(dto.description());
        }

        if (dto.category() != null) {
            document.setCategory(dto.category());
        }

        if (dto.status() != null) {
            document.setStatus(dto.status());
        }

        return toDefinitionResponse(documentDefinitionRepository.save(document));
    }

    // ============================================================
    // VERSIONS
    // ============================================================
    @Transactional
    public DocumentVersionResponseVM createVersion(Long documentId, DocumentVersionDTO dto) {

        DocumentDefinition document =
                getdocumentDefinition(documentId);

        validateVersion(dto);

        MultipartFile file = dto.file();

        Instant now = Instant.now();

        String mime = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();

        long size = file.getSize();

        String originalName = getOriginalName(file);

        Integer nextVersion =
                documentVersionRepository
                        .findFirstByDocumentDefinition_IdOrderByVersionDesc(documentId)
                        .map(version -> version.getVersion() + 1)
                        .orElse(1);

        String safeFileName =
                UUID.randomUUID() + "_" + originalName;

        String key =
                "clinical-documents/"
                        + documentId
                        + "/"
                        + nextVersion
                        + "/"
                        + YYYY.format(now)
                        + "/"
                        + MM.format(now)
                        + "/"
                        + safeFileName;

        // Upload file to DigitalOcean Spaces
        try {

            log.debug("Uploading clinical document template: documentId={}, version={}, key={}", documentId, nextVersion, key);

            storage.put(key, mime, size, file.getInputStream());

        } catch (Exception e) {

            throw new BadRequestAlertException("template.uploadFailed",
                    "clinical-document",
                    "Document template upload failed for documentId=" + documentId + ", version=" + nextVersion + "/n" + e.getMessage()

            );
        }

        // Deactivate previous versions
        List<DocumentVersion> previousVersions =
                documentVersionRepository
                        .findAllByDocumentDefinition_Id(documentId);

        previousVersions.forEach(version ->
                version.setStatus(DocumentStatus.INACTIVE)
        );

        documentVersionRepository.saveAll(previousVersions);
        String login = currentUsername();
        // Create new version
        DocumentVersion version =
                new DocumentVersion();

        version.setDocumentDefinition(document);
        version.setVersion(nextVersion);
        version.setFileName(originalName);
        version.setMimeType(mime);
        version.setSpaceKey(key);
        version.setStatus(DocumentStatus.ACTIVE);
        version.setSizeBytes(size);
        version.setEffectiveFrom(dto.effectiveFromDate() != null ? dto.effectiveFromDate() : now);
        version.setEffectiveTo(dto.effectiveToDate());
        version.setCreatedBy(login);
        version.setCreatedDate(now);

        DocumentVersion saved =
                documentVersionRepository.save(version);

        return toVersionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DocumentVersionResponseVM> getVersions(Long documentId) {

        getdocumentDefinition(documentId);

        return documentVersionRepository
                .findAllByDocumentDefinition_IdOrderByCreatedDateDesc(documentId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
    }

    // ============================================================
    // ASSIGNMENTS
    // ============================================================
    @Transactional
    public DocumentAssignmentResponseVM createAssignment(Long documentId, DocumentAssignmentDTO dto) {

        DocumentDefinition document = getdocumentDefinition(documentId);

        boolean exists;

        if (dto.targetId() == null) {

            // General assignment:
            // Don't allow another general assignment
            // OR any specific assignment for this target type.
            exists =
                    assignmentRepository
                            .existsByDocumentDefinition_IdAndTargetTypeAndTargetIdIsNull(
                                    documentId,
                                    dto.targetType()
                            )
                            ||
                            assignmentRepository
                                    .existsByDocumentDefinition_IdAndTargetTypeAndTargetIdIsNotNull(
                                            documentId,
                                            dto.targetType()
                                    );

        } else {

            // Specific assignment:
            // Don't allow if a general assignment exists
            // OR the same specific target already exists.
            exists =
                    assignmentRepository
                            .existsByDocumentDefinition_IdAndTargetTypeAndTargetIdIsNull(
                                    documentId,
                                    dto.targetType()
                            )
                            ||
                            assignmentRepository
                                    .existsByDocumentDefinition_IdAndTargetTypeAndTargetId(
                                            documentId,
                                            dto.targetType(),
                                            dto.targetId()
                                    );
        }

        if (exists) {
            throw new BadRequestAlertException(
                    "Document assignment already exists for this target type" ,
                    "clinical-document",
                    "assignment.exists"
            );
        }

        DocumentAssignment assignment = new DocumentAssignment();

        assignment.setDocumentDefinition(document);
        assignment.setTargetType(dto.targetType());
        assignment.setTargetId(dto.targetId());
        assignment.setTriggerType(dto.triggerType());
        assignment.setRequired(dto.required());
        assignment.setBlocking(dto.blocking());
        assignment.setActive(dto.active() == null || dto.active());

        DocumentAssignment saved = assignmentRepository.save(assignment);

        DocumentVersionResponseVM activeVersion =
                documentVersionRepository.findFirstByDocumentDefinition_IdAndStatus(document.getId(), DocumentStatus.ACTIVE)
                        .map(this::toVersionResponse)
                        .orElse(null);

        return toAssignmentResponse(saved, activeVersion);
    }

    @Transactional
    public void deleteAssignment(Long assignmentId) {

        DocumentAssignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "assignment.notFound" ,
                                        "clinical-document",
                                       "Document assignment not found: " + assignmentId
                                )
                        );

        assignmentRepository.delete(assignment);
    }

    @Transactional
    public DocumentAssignmentResponseVM toggleAssignmentActive(Long assignmentId) {

        DocumentAssignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() ->
                                new NotFoundAlertException(
                                        "Document assignment not found: " + assignmentId,
                                        "clinical-document",
                                        "assignment.notFound"
                                )
                        );

        assignment.setActive(!assignment.getActive());

        DocumentAssignment saved =
                assignmentRepository.save(assignment);

        DocumentVersionResponseVM activeVersion =
                documentVersionRepository
                        .findFirstByDocumentDefinition_IdAndStatus(
                                assignment.getDocumentDefinition().getId(),
                                DocumentStatus.ACTIVE
                        )
                        .map(this::toVersionResponse)
                        .orElse(null);

        return toAssignmentResponse(
                saved,
                activeVersion
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentAssignmentResponseVM> getAssignments(Long documentId) {

        DocumentDefinition documentDefinition = getdocumentDefinition(documentId);
        DocumentVersionResponseVM activeVersion = documentVersionRepository.findFirstByDocumentDefinition_IdAndStatus(
                documentDefinition.getId(),
                DocumentStatus.ACTIVE
        ).map(this::toVersionResponse).orElse(null);

        return assignmentRepository
                .findAllByDocumentDefinitionId(documentId)
                .stream()
                .map(assignment -> toAssignmentResponse(assignment, activeVersion))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentAssignmentResponseVM> searchAssignments(
            DocumentTargetType targetType,
            Long targetId
    ) {
        List<DocumentAssignment> assignments;

        // Always get the general assignments (targetId = null)
        List<DocumentAssignment> generalAssignments =
                assignmentRepository.findAllByTargetTypeAndTargetIdIsNull(targetType);

        if (targetId == null) {
            assignments = generalAssignments;
        } else {
            // Get assignments for the specific targetId
            List<DocumentAssignment> specificAssignments =
                    assignmentRepository.findAllByTargetTypeAndTargetIdIn(
                            targetType,
                            List.of(targetId)
                    );

            assignments = new java.util.ArrayList<>(generalAssignments);
            assignments.addAll(specificAssignments);
        }

        return assignments.stream()
                .map(assignment -> {

                    DocumentVersionResponseVM activeVersion =
                            documentVersionRepository
                                    .findFirstByDocumentDefinition_IdAndStatus(
                                            assignment.getDocumentDefinition().getId(),
                                            DocumentStatus.ACTIVE
                                    )
                                    .map(this::toVersionResponse)
                                    .orElse(null);

                    return toAssignmentResponse(
                            assignment,
                            activeVersion
                    );
                })
                .toList();
    }
    // ============================================================
    // RUNTIME - REQUIREMENTS
    // ============================================================

    @Transactional(readOnly = true)
    public List<DocumentRequirementVM> resolveRequirements(DocumentTargetType sourceType, Long sourceId) {

        List<DocumentAssignment> assignments =
                assignmentRepository
                        .findAllByTargetTypeAndTargetIdAndActiveTrue(
                                sourceType,
                                sourceId
                        );

        return assignments.stream()
                .map(this::toRequirementDTO)
                .toList();
    }

    // ============================================================
    // TEMPLATE
    // ============================================================

    @Transactional(readOnly = true)
    public DocumentTemplateDownloadVM downloadTemplate(Long documentId) {

        DocumentVersion version =
                documentVersionRepository
                        .findFirstByDocumentDefinition_IdAndStatus(
                                documentId,
                                DocumentStatus.ACTIVE
                        )
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "activeVersion.notFound",
                                        "clinical-document",
                                        "No active document version"
                                )
                        );

        PresignedGetObjectRequest getURL =
                storage.presignGet(
                        version.getSpaceKey(),
                        version.getFileName()
                );

        return new DocumentTemplateDownloadVM(
                getURL.url().toString(),
                props.getPresignExpirySeconds()
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private DocumentDefinition getdocumentDefinition(Long id) {
        return documentDefinitionRepository
                .findById(id)
                .orElseThrow(() ->
                        new NotFoundAlertException(
                                "Clinical document not found: " + id, "clinical-document", "notFound"
                        ));
    }

    private DocumentRequirementVM toRequirementDTO(DocumentAssignment assignment) {

        DocumentDefinition document = assignment.getDocumentDefinition();
        DocumentVersion version = documentVersionRepository.findFirstByDocumentDefinition_IdAndStatus(
                document.getId(),
                DocumentStatus.ACTIVE
        ).orElse(null);

        return new DocumentRequirementVM(
                assignment.getId(),
                document.getId(),
                document.getCode(),
                document.getName(),
                version != null ? version.getId() : null,
                version != null ? version.getVersion() : null,
                assignment.getRequired(),
                assignment.getBlocking(),
                resolveRequirementStatus(assignment)
        );
    }

    private DocumentRequirementStatus resolveRequirementStatus(DocumentAssignment assignment) {
        /*
         * Later this should check the patient's/encounter's
         * existing clinical document attachment.
         */
        return DocumentRequirementStatus.PENDING;
    }

    private DocumentDefinitionResponseVM toDefinitionResponse(DocumentDefinition entity) {
        return toDefinitionResponse(entity, null);
    }

    private DocumentDefinitionResponseVM toDefinitionResponse(DocumentDefinition entity, DocumentVersion activeVersion) {

        return new DocumentDefinitionResponseVM(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getStatus(),
                activeVersion != null
                        ? toVersionResponse(activeVersion)
                        : null
        );
    }

    private DocumentVersionResponseVM toVersionResponse(DocumentVersion entity) {

        return new DocumentVersionResponseVM(
                entity.getId(),
                entity.getDocumentDefinition().getId(),
                entity.getVersion(),
                entity.getFileName(),
                entity.getMimeType(),
                entity.getStatus(),
                entity.getCreatedDate(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo()
        );
    }

    private DocumentAssignmentResponseVM toAssignmentResponse(DocumentAssignment entity, DocumentVersionResponseVM activeVersion) {

        return new DocumentAssignmentResponseVM(
                entity.getId(),
                entity.getDocumentDefinition().getId(),
                entity.getDocumentDefinition().getName(),
                entity.getDocumentDefinition().getCode(),
                activeVersion,
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getTriggerType(),
                entity.getRequired(),
                entity.getBlocking(),
                entity.getActive()
        );
    }

    private void validateDocumentCode(String code) {

        if (code == null || code.isBlank()) {
            throw new BadRequestAlertException(
                    "code.required",
                    "clinical-document",
                    "document code is required"
            );
        }

        if (documentDefinitionRepository.existsByCode(code)) {
            throw new BadRequestAlertException(
                    "code.exists",
                    "clinical-document",
                    "document code already exists"
            );
        }
    }

    private void validateVersion(DocumentVersionDTO dto) {

        if (dto == null || dto.file() == null || dto.file().isEmpty()) {
            throw new BadRequestAlertException(
                    "No template file provided",
                    "clinical-document",
                    "template.required"
            );
        }

        MultipartFile file = dto.file();

        String mime = file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();

        if (!props.getAllowed().contains(mime)) {
            throw new BadRequestAlertException(
                    "Unsupported file type",
                    "clinical-document",
                    "unsupported_type"
            );
        }

        if (file.getSize() > props.getMaxBytes()) {
            throw new BadRequestAlertException(
                    "File too large",
                    "clinical-document",
                    "too_large"
            );
        }
    }

    private String getOriginalName(MultipartFile file) {

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            return "document";
        }

        return Paths.get(originalName)
                .getFileName()
                .toString();
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException("No authenticated user", "documents", "unauthenticated"));
    }

}