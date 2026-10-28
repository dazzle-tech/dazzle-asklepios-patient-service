package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import com.dazzle.asklepios.service.PatientComplaintPublicLookupService;
import com.dazzle.asklepios.service.PatientComplaintService;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintActionCompleteDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintActionCreateDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintAssignDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintDeadlineDTO;
import com.dazzle.asklepios.security.AuthoritiesConstants;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintMobileSubmitDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintPublicLookupRequestDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintReopenDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintResolveDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintSubmitDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintAttachmentDownloadVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintDetailVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintListVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintPublicLookupVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintSubmitResponseVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintSummaryVM;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Slf4j
public class PatientComplaintController {

    private final PatientComplaintService patientComplaintService;
    private final PatientComplaintPublicLookupService patientComplaintPublicLookupService;

    @PostMapping("/patient-complaints/lookup-by-document")
    public ResponseEntity<PatientComplaintPublicLookupVM> lookupByDocument(
        @Valid @RequestBody PatientComplaintPublicLookupRequestDTO request,
        HttpServletRequest httpRequest
    ) {
        String clientKey = resolveClientKey(httpRequest);
        return ResponseEntity.ok(patientComplaintPublicLookupService.lookup(request, clientKey));
    }

    @PostMapping(value = "/patient-complaints/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientComplaintSubmitResponseVM> submitJson(
        @Valid @RequestBody PatientComplaintSubmitDTO dto
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(patientComplaintService.submit(dto, List.of()));
    }

    /** Multipart variant of public submit; attachments stay scoped to complaint creation. */
    @PostMapping(value = "/patient-complaints/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatientComplaintSubmitResponseVM> submitMultipart(
        @Valid @RequestPart("complaint") PatientComplaintSubmitDTO dto,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(patientComplaintService.submit(dto, files == null ? List.of() : files));
    }

    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.PATIENT + "')")
    @PostMapping(value = "/patient-complaints/mobile/submit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PatientComplaintSubmitResponseVM> submitMobileJson(
        @Valid @RequestBody PatientComplaintMobileSubmitDTO dto
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(patientComplaintService.submitMobile(dto, List.of()));
    }

    /** Multipart mobile submit; part {@code complaint} + optional {@code files}. */
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.PATIENT + "')")
    @PostMapping(value = "/patient-complaints/mobile/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PatientComplaintSubmitResponseVM> submitMobileMultipart(
        @Valid @RequestPart("complaint") PatientComplaintMobileSubmitDTO dto,
        @RequestPart(value = "files", required = false) List<MultipartFile> files
    ) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(patientComplaintService.submitMobile(dto, files == null ? List.of() : files));
    }

    @GetMapping("/patient-complaints/{id}/attachments/{attachmentId}/download-url")
    public ResponseEntity<PatientComplaintAttachmentDownloadVM> downloadAttachment(
        @PathVariable Long id,
        @PathVariable Long attachmentId
    ) {
        return ResponseEntity.ok(patientComplaintService.getAttachmentDownloadUrl(id, attachmentId));
    }

    @GetMapping("/patient-complaints")
    public ResponseEntity<List<PatientComplaintListVM>> search(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) PatientComplaintStatus status,
        @RequestParam(required = false) Long assignedUserId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant receivedTo,
        @RequestParam(required = false) String summaryFilter,
        Pageable pageable
    ) {
        Page<PatientComplaintListVM> page = patientComplaintService.search(
            search,
            status,
            assignedUserId,
            receivedFrom,
            receivedTo,
            summaryFilter,
            pageable
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
            ServletUriComponentsBuilder.fromCurrentRequest(),
            page
        );
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("/patient-complaints/summary")
    public ResponseEntity<PatientComplaintSummaryVM> summary() {
        return ResponseEntity.ok(patientComplaintService.summary());
    }

    @GetMapping("/patient-complaints/{id}")
    public ResponseEntity<PatientComplaintDetailVM> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(patientComplaintService.getDetail(id));
    }

    @PutMapping("/patient-complaints/{id}/assign")
    public ResponseEntity<PatientComplaintDetailVM> assign(
        @PathVariable Long id,
        @Valid @RequestBody PatientComplaintAssignDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.assign(id, dto));
    }

    @PutMapping("/patient-complaints/{id}/deadline")
    public ResponseEntity<PatientComplaintDetailVM> setDeadline(
        @PathVariable Long id,
        @Valid @RequestBody PatientComplaintDeadlineDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.setDeadline(id, dto));
    }

    @PostMapping("/patient-complaints/{id}/start-handling")
    public ResponseEntity<PatientComplaintDetailVM> startHandling(@PathVariable Long id) {
        return ResponseEntity.ok(patientComplaintService.startHandling(id));
    }

    @PostMapping("/patient-complaints/{id}/actions")
    public ResponseEntity<PatientComplaintDetailVM> addAction(
        @PathVariable Long id,
        @Valid @RequestBody PatientComplaintActionCreateDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.addAction(id, dto));
    }

    @PostMapping("/patient-complaints/{id}/actions/{actionId}/start")
    public ResponseEntity<PatientComplaintDetailVM> startAction(
        @PathVariable Long id,
        @PathVariable Long actionId
    ) {
        return ResponseEntity.ok(patientComplaintService.startAction(id, actionId));
    }

    @PostMapping("/patient-complaints/{id}/actions/{actionId}/complete")
    public ResponseEntity<PatientComplaintDetailVM> completeAction(
        @PathVariable Long id,
        @PathVariable Long actionId,
        @Valid @RequestBody PatientComplaintActionCompleteDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.completeAction(id, actionId, dto));
    }

    @PostMapping("/patient-complaints/{id}/resolve")
    public ResponseEntity<PatientComplaintDetailVM> resolve(
        @PathVariable Long id,
        @Valid @RequestBody PatientComplaintResolveDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.resolve(id, dto));
    }

    @PostMapping("/patient-complaints/{id}/close")
    public ResponseEntity<PatientComplaintDetailVM> close(@PathVariable Long id) {
        return ResponseEntity.ok(patientComplaintService.close(id));
    }

    @PostMapping("/patient-complaints/{id}/reopen")
    public ResponseEntity<PatientComplaintDetailVM> reopen(
        @PathVariable Long id,
        @Valid @RequestBody PatientComplaintReopenDTO dto
    ) {
        return ResponseEntity.ok(patientComplaintService.reopen(id, dto));
    }

    private static String resolveClientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
