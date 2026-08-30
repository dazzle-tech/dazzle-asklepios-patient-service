package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.DocumentStatus;
import com.dazzle.asklepios.domain.enumeration.DocumentTargetType;
import com.dazzle.asklepios.service.DocumentManagementService;
import com.dazzle.asklepios.service.dto.documentAssignment.DocumentAssignmentDTO;
import com.dazzle.asklepios.service.dto.documentDefinition.DocumentDefinitionDTO;
import com.dazzle.asklepios.service.dto.documentVersion.DocumentVersionDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.documentAssignment.DocumentAssignmentResponseVM;
import com.dazzle.asklepios.web.rest.vm.documentAssignment.DocumentRequirementVM;
import com.dazzle.asklepios.web.rest.vm.documentDefinition.DocumentDefinitionResponseVM;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentTemplateDownloadVM;
import com.dazzle.asklepios.web.rest.vm.documentVersion.DocumentVersionResponseVM;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class DocumentManagementController {

    private final DocumentManagementService documentService;

    // ============================================================
    // document ADMINISTRATION
    // ============================================================

    @GetMapping("/documents-management/search")
    public ResponseEntity<Page<DocumentDefinitionResponseVM>> search(@RequestParam(required = false) String search, @RequestParam(required = false) DocumentStatus status, Pageable pageable
    ) {
        return ResponseEntity.ok(
                documentService.searchDocuments(
                        search,
                        status,
                        pageable
                )
        );
    }

    @PostMapping("/documents-management")
    public ResponseEntity<DocumentDefinitionResponseVM> create(@Valid @RequestBody DocumentDefinitionDTO dto) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        documentService.createDocument(dto)
                );
    }

    @GetMapping("/documents-management/{id}")
    public ResponseEntity<DocumentDefinitionResponseVM> get(@PathVariable Long id) {

        return ResponseEntity.ok(
                documentService.getDocument(id)
        );
    }

    @PutMapping("/documents-management/{id}")
    public ResponseEntity<DocumentDefinitionResponseVM> update(@PathVariable Long id, @Valid @RequestBody DocumentDefinitionDTO dto) {

        return ResponseEntity.ok(documentService.updateDocument(id, dto));
    }

    // ============================================================
    // VERSIONS
    // ============================================================

    @PostMapping(value = "/documents-management/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentVersionResponseVM> createVersion(@PathVariable Long documentId, @RequestPart("file") MultipartFile file, @RequestParam("effectiveFromDate") String effectiveFromDate,
                                                                   @RequestParam(value = "effectiveToDate", required = false) String effectiveToDate) {

        Instant effectiveFrom;
        Instant effectiveTo = null;

        try {
            effectiveFrom = Instant.parse(effectiveFromDate);
            if (effectiveToDate != null && !effectiveToDate.isEmpty()) {
                effectiveTo = Instant.parse(effectiveToDate);
            }
        } catch (Exception e) {
            throw new BadRequestAlertException(
                    "Invalid effectiveFromDate. Expected ISO-8601 format.",
                    "clinical-document",
                    "effectiveFromDate.invalid"
            );
        }

        DocumentVersionDTO dto = new DocumentVersionDTO(file, effectiveFrom, effectiveTo);

        DocumentVersionResponseVM result = documentService.createVersion(documentId, dto);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(result);
    }

    @GetMapping("/documents-management/{id}/versions")
    public ResponseEntity<List<DocumentVersionResponseVM>> getVersions(@PathVariable Long id) {

        return ResponseEntity.ok(documentService.getVersions(id));
    }

    // ============================================================
    // ASSIGNMENTS
    // ============================================================

    @PostMapping("/documents-management/{id}/assignments")
    public ResponseEntity<DocumentAssignmentResponseVM> createAssignment(@PathVariable Long id, @Valid @RequestBody DocumentAssignmentDTO dto) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        documentService.createAssignment(
                                id,
                                dto
                        )
                );
    }

    @PutMapping("/documents-management/assignments/{assignmentId}/toggle-active")
    public ResponseEntity<DocumentAssignmentResponseVM> toggleAssignmentActive(
            @PathVariable Long assignmentId
    ) {
        return ResponseEntity.ok(
                documentService.toggleAssignmentActive(assignmentId)
        );
    }

    @DeleteMapping("/documents-management/assignments/{assignmentId}")
    public ResponseEntity<Void> deleteAssignment(@PathVariable Long assignmentId) {

        documentService.deleteAssignment(assignmentId);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/documents-management/{id}/assignments")
    public ResponseEntity<List<DocumentAssignmentResponseVM>> getAssignments(@PathVariable Long id) {

        return ResponseEntity.ok(documentService.getAssignments(id));
    }

    @GetMapping("/documents-management/assignments/search")
    public ResponseEntity<List<DocumentAssignmentResponseVM>> searchAssignments(
            @RequestParam DocumentTargetType targetType,
            @RequestParam(required = false) Long targetId
    ) {
        return ResponseEntity.ok(
                documentService.searchAssignments(
                        targetType,
                        targetId
                )
        );
    }

    // ============================================================
    // RUNTIME
    // ============================================================

    @GetMapping("/documents-management/requirements")
    public ResponseEntity<List<DocumentRequirementVM>> getRequirements(@RequestParam DocumentTargetType sourceType, @RequestParam Long sourceId) {

        return ResponseEntity.ok(documentService.resolveRequirements(sourceType, sourceId));
    }

    @GetMapping("/documents-management/{id}/template")
    public ResponseEntity<DocumentTemplateDownloadVM> downloadTemplate(@PathVariable Long id) {

        return ResponseEntity.ok(documentService.downloadTemplate(id));
    }


}
