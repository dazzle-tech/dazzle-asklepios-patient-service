
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.repository.DiagnosticTestRequestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestUpdateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DiagnosticTestRequestService {

    private final DiagnosticTestRequestRepository repository;

    public DiagnosticTestRequestService(DiagnosticTestRequestRepository repository) {
        this.repository = repository;
    }

    public DiagnosticTestRequest create(DiagnosticTestRequestCreateDTO dto, String username) {
        DiagnosticTestRequest e = new DiagnosticTestRequest();
        e.setStatus(DiagnosticTestRequestStatus.REQUESTED);
        e.setType(dto.type());
        e.setName(dto.name());
        e.setIndication(dto.indication());
        e.setFromDepartmentId(dto.fromDepartmentId());
        e.setFromFacilityId(dto.fromFacilityId());

        Instant now = Instant.now();
        e.setCreatedBy(username);
        e.setCreatedDate(now);
        e.setLastModifiedBy(username);
        e.setLastModifiedDate(now);

        return repository.save(e);
    }

    public DiagnosticTestRequest update(DiagnosticTestRequestUpdateDTO dto, String username) {
        DiagnosticTestRequest e = get(dto.id());

        ensureOwner(e, username, "update_not_allowed", "Only the creator can update this request");

        if (e.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "locked",
                    "diagnostic_test_requests",
                    "Cannot update a non-REQUESTED request"
            );
        }

        e.setType(dto.type());
        e.setName(dto.name());
        e.setIndication(dto.indication());
        e.setFromDepartmentId(dto.fromDepartmentId());
        e.setFromFacilityId(dto.fromFacilityId());

        e.setLastModifiedBy(username);
        e.setLastModifiedDate(Instant.now());

        return repository.save(e);
    }

    public DiagnosticTestRequest approve(Long id, String username) {
        DiagnosticTestRequest e = get(id);

        if (e.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_test_requests",
                    "Only REQUESTED requests can be approved"
            );
        }

        e.setStatus(DiagnosticTestRequestStatus.APPROVED);
        e.setApprovedBy(username);
        e.setApprovedDate(Instant.now());
        e.setLastModifiedBy(username);
        e.setLastModifiedDate(Instant.now());

        return repository.save(e);
    }

    public DiagnosticTestRequest reject(Long id, String username, String rejectedReason) {
        DiagnosticTestRequest e = get(id);

        if (e.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_test_requests",
                    "Only REQUESTED requests can be rejected"
            );
        }

        e.setStatus(DiagnosticTestRequestStatus.REJECTED);
        e.setRejectedBy(username);
        e.setRejectedReason(rejectedReason);
        e.setRejectedDate(Instant.now());
        e.setLastModifiedBy(username);
        e.setLastModifiedDate(Instant.now());

        return repository.save(e);
    }

    public void delete(Long id, String username) {
        DiagnosticTestRequest e = get(id);

        ensureOwner(e, username, "delete_not_allowed", "Only the creator can delete this request");

        if (e.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "locked",
                    "diagnostic_test_requests",
                    "Cannot delete a non-REQUESTED request"
            );
        }

        repository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public DiagnosticTestRequest get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_test_requests",
                        "DiagnosticTestRequest not found with id " + id
                ));
    }

    private void ensureOwner(DiagnosticTestRequest e, String username, String errorKey, String message) {
        String owner = e.getCreatedBy();
        if (owner == null || !owner.equals(username)) {
            throw new BadRequestAlertException(errorKey, "diagnostic_test_requests", message);
        }
    }
}
