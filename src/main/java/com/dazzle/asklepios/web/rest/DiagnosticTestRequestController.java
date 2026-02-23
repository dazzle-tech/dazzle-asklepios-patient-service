package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.domain.enumeration.TestType;
import com.dazzle.asklepios.repository.DiagnosticTestRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.DiagnosticTestRequestService;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestUpdateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.commands.DiagnosticTestRequestLinkDiagnosticTestDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.commands.DiagnosticTestRequestRejectDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/patient")
public class DiagnosticTestRequestController {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticTestRequestController.class);

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
    public ResponseEntity<DiagnosticTestRequest> create(@Valid @RequestBody DiagnosticTestRequestCreateDTO dto) {
        LOG.debug("[DiagnosticTestRequest] CREATE - request received. payload={}", dto);
        String username = currentUsername();
        DiagnosticTestRequest saved = service.create(dto, username);

        LOG.debug("[DiagnosticTestRequest] CREATE - created successfully. id={}", saved.getId());
        return ResponseEntity
                .created(URI.create("/api/patient/diagnostic-test-requests/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<DiagnosticTestRequest> update(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticTestRequestUpdateDTO dto
    ) {
        LOG.debug("[DiagnosticTestRequest] UPDATE - request received. id={} payload={}", id, dto);
        if (!id.equals(dto.id())) {
            throw new BadRequestAlertException("idmismatch", "diagnostic_test_requests", "Path id and body id mismatch");
        }

        String username = currentUsername();
        DiagnosticTestRequest updated = service.update(dto, username);
        LOG.debug("[DiagnosticTestRequest] UPDATE - updated successfully. id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<DiagnosticTestRequest> getTestRequestById(@PathVariable Long id) {
        LOG.debug("[DiagnosticTestRequest] GET_BY_ID - request received. id={}", id);
        DiagnosticTestRequest existing = service.getDiagnosticTestRequestById(id);
        LOG.debug("[DiagnosticTestRequest] GET_BY_ID - found. id={}", existing.getId());
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/diagnostic-test-requests/{id}")
    public ResponseEntity<Void> delete(@Valid @PathVariable Long id) {
        LOG.debug("[DiagnosticTestRequest] DELETE - request received. id={}", id);
        String username = currentUsername();
        service.delete(id, username);
        LOG.debug("[DiagnosticTestRequest] DELETE - deleted successfully. id={}", id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/diagnostic-test-requests/{id}/approve")
    public ResponseEntity<DiagnosticTestRequest> approve(@Valid @PathVariable Long id) {
        LOG.debug("[DiagnosticTestRequest] APPROVE - request received. id={}", id);
        String username = currentUsername();
        DiagnosticTestRequest updated = service.approve(id, username);
        LOG.debug("[DiagnosticTestRequest] APPROVE - approved successfully. id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/diagnostic-test-requests/{id}/reject")
    public ResponseEntity<DiagnosticTestRequest> reject(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticTestRequestRejectDTO dto
    ) {
        LOG.debug("[DiagnosticTestRequest] REJECT - request received. id={} payload={}", id, dto);
        String username = currentUsername();
        DiagnosticTestRequest updated = service.reject(id, username, dto.rejectedReason());
        LOG.debug("[DiagnosticTestRequest] REJECT - rejected successfully. id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/diagnostic-test-requests")
    public ResponseEntity<List<DiagnosticTestRequest>> filter(
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
        LOG.debug("[DiagnosticTestRequest] FILTER - request received. status={} type={} name={} fromDepartmentId={} fromFacilityId={} createdBy={} createdDateFrom={} createdDateTo={} pageable={}",
                status, type, name, fromDepartmentId, fromFacilityId, createdBy, createdDateFrom, createdDateTo, pageable);

        Specification<DiagnosticTestRequest> filterSpec = (requestRoot, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) predicates.add(criteriaBuilder.equal(requestRoot.get("status"), status));
            if (type != null) predicates.add(criteriaBuilder.equal(requestRoot.get("type"), type));

            if (name != null && !name.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(requestRoot.get("name")),
                        "%" + name.trim().toLowerCase() + "%"
                ));
            }

            if (fromDepartmentId != null)
                predicates.add(criteriaBuilder.equal(requestRoot.get("fromDepartmentId"), fromDepartmentId));
            if (fromFacilityId != null)
                predicates.add(criteriaBuilder.equal(requestRoot.get("fromFacilityId"), fromFacilityId));
            if (createdBy != null && !createdBy.isBlank())
                predicates.add(criteriaBuilder.equal(requestRoot.get("createdBy"), createdBy));

            if (createdDateFrom != null)
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(requestRoot.get("createdDate"), createdDateFrom));
            if (createdDateTo != null)
                predicates.add(criteriaBuilder.lessThanOrEqualTo(requestRoot.get("createdDate"), createdDateTo));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<DiagnosticTestRequest> page = repository.findAll(filterSpec, pageable);

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page
        );

        LOG.debug("[DiagnosticTestRequest] FILTER - response ready. returned={} totalElements={} totalPages={}",
                page.getNumberOfElements(), page.getTotalElements(), page.getTotalPages());

        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }



    @PutMapping("/diagnostic-test-requests/{id}/diagnostic-test")
    public ResponseEntity<DiagnosticTestRequest> setDiagnosticTest(
            @PathVariable Long id,
            @Valid @RequestBody DiagnosticTestRequestLinkDiagnosticTestDTO dto
    ) {

        return ResponseEntity.ok(service.setDiagnosticTest(id, dto.diagnosticTestId()));
    }
}
