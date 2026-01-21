
package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticTestRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticTestRequestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestUpdateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.commands.DiagnosticTestRequestRejectDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.vm.diagnosticorders.requests.DiagnosticTestRequestResponseVM;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticTestRequestController {

    private final DiagnosticTestRequestService service;
    private final DiagnosticTestRequestRepository repository;

    public DiagnosticTestRequestController(
            DiagnosticTestRequestService service,
            DiagnosticTestRequestRepository repository
    ) {
        this.service = service;
        this.repository = repository;
    }

    private String currentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "unauthenticated",
                        "diagnostic_test_requests",
                        "No authenticated user"
                ));
    }

    @PostMapping("/diagnostic-test-requests")
    public ResponseEntity<DiagnosticTestRequestResponseVM> create(@Valid @RequestBody DiagnosticTestRequestCreateDTO dto) {
        String username = currentUsername();
        DiagnosticTestRequest saved = service.create(dto, username);

        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-test-requests/" + saved.getId()))
                .body(DiagnosticTestRequestResponseVM.ofEntity(saved));
    }

    @PutMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<DiagnosticTestRequestResponseVM> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticTestRequestUpdateDTO dto
    ) {
        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("idmismatch", "diagnostic_test_requests", "Path id and body id mismatch");
        }
        String username = currentUsername();
        DiagnosticTestRequest updated = service.update(dto, username);
        return ResponseEntity.ok(DiagnosticTestRequestResponseVM.ofEntity(updated));
    }

    @GetMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<DiagnosticTestRequestResponseVM> get(@PathVariable Long id) {
        return ResponseEntity.ok(DiagnosticTestRequestResponseVM.ofEntity(service.get(id)));
    }

    @DeleteMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String username = currentUsername();
        service.delete(id, username);
        return ResponseEntity.noContent().build();
    }

    // Approve WITHOUT body
    @PostMapping("/diagnostic-test-requests/{id}/approve")
    public ResponseEntity<DiagnosticTestRequestResponseVM> approve(@PathVariable Long id) {
        String username = currentUsername();
        DiagnosticTestRequest updated = service.approve(id, username);
        return ResponseEntity.ok(DiagnosticTestRequestResponseVM.ofEntity(updated));
    }

    @PostMapping("/diagnostic-test-requests/{id}/reject")
    public ResponseEntity<DiagnosticTestRequestResponseVM> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticTestRequestRejectDTO dto
    ) {
        String username = currentUsername();
        DiagnosticTestRequest updated = service.reject(id, username, dto.rejectedReason());
        return ResponseEntity.ok(DiagnosticTestRequestResponseVM.ofEntity(updated));
    }

    // Optional filter endpoint
    @GetMapping("/diagnostic-test-requests")
    public ResponseEntity<List<DiagnosticTestRequestResponseVM>> filter(
            @RequestParam(name = "status", required = false) DiagnosticTestRequestStatus status,
            @RequestParam(name = "type", required = false) TestType type,
            @RequestParam(name = "name", required = false) String name,
            @RequestParam(name = "fromDepartmentId", required = false) Long fromDepartmentId,
            @RequestParam(name = "fromFacilityId", required = false) Long fromFacilityId,
            @RequestParam(name = "createdBy", required = false) String createdBy,
            @RequestParam(name = "createdDateFrom", required = false) Instant createdDateFrom,
            @RequestParam(name = "createdDateTo", required = false) Instant createdDateTo,
            @ParameterObject Pageable pageable
    ) {
        Specification<DiagnosticTestRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (name != null && !name.isBlank())
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            if (fromDepartmentId != null) predicates.add(cb.equal(root.get("fromDepartmentId"), fromDepartmentId));
            if (fromFacilityId != null) predicates.add(cb.equal(root.get("fromFacilityId"), fromFacilityId));
            if (createdBy != null && !createdBy.isBlank()) predicates.add(cb.equal(root.get("createdBy"), createdBy));
            if (createdDateFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdDate"), createdDateFrom));
            if (createdDateTo != null) predicates.add(cb.lessThanOrEqualTo(root.get("createdDate"), createdDateTo));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticTestRequest> page = repository.findAll(spec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        List<DiagnosticTestRequestResponseVM> body = page.getContent()
                .stream()
                .map(DiagnosticTestRequestResponseVM::ofEntity)
                .toList();

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
