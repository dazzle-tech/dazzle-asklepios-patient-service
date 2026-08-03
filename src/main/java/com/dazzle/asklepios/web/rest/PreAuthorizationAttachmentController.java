package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.service.PreAuthorizationAttachmentService;
import com.dazzle.asklepios.web.rest.vm.preauth.DownloadPreAuthorizationAttachmentVM;
import com.dazzle.asklepios.web.rest.vm.preauth.PreAuthorizationAttachmentResponse;
import com.dazzle.asklepios.web.rest.vm.preauth.UploadPreAuthorizationAttachmentVM;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationAttachmentController {

    private final PreAuthorizationAttachmentService service;

    @PostMapping(
            value = "/internal/waseel/pre-authorizations/{preAuthorizationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<PreAuthorizationAttachmentResponse> upload(
            @PathVariable Long preAuthorizationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "sourceId", required = false) Long sourceId
    ) {
        log.debug("Upload pre-authorization attachment. preAuthId={}", preAuthorizationId);
        PreAuthorizationAttachmentResponse saved = service.upload(
                preAuthorizationId,
                new UploadPreAuthorizationAttachmentVM(file, type, details, source, sourceId)
        );
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/internal/waseel/pre-authorizations/{preAuthorizationId}/attachments")
    public ResponseEntity<List<PreAuthorizationAttachmentResponse>> list(
            @PathVariable Long preAuthorizationId
    ) {
        return ResponseEntity.ok(service.list(preAuthorizationId));
    }

    @PostMapping("/internal/waseel/pre-authorizations/attachments/{id}/download-url")
    public ResponseEntity<DownloadPreAuthorizationAttachmentVM> downloadUrl(@PathVariable Long id) {
        return ResponseEntity.ok(service.downloadUrl(id));
    }

    @GetMapping("/internal/waseel/pre-authorizations/attachments/{id}/file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        return service.downloadFile(id);
    }

    @DeleteMapping("/internal/waseel/pre-authorizations/attachments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
