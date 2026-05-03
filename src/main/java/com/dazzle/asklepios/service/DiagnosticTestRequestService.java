
package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.DiagnosticTestRequest;
import com.dazzle.asklepios.domain.enumeration.DiagnosticTestRequestStatus;
import com.dazzle.asklepios.repository.DiagnosticTestRequestRepository;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestCreateDTO;
import com.dazzle.asklepios.service.dto.medicalsheets.diagnosticorders.requests.DiagnosticTestRequestUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class DiagnosticTestRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticTestRequestService.class);

    private final DiagnosticTestRequestRepository repository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;

    public DiagnosticTestRequestService(DiagnosticTestRequestRepository repository, FacilityHelper facilityHelper, DepartmentHelper departmentHelper) {
        this.repository = repository;
        this.facilityHelper = facilityHelper;
        this.departmentHelper = departmentHelper;
    }

    public DiagnosticTestRequest create(DiagnosticTestRequestCreateDTO dto, String username) {
        LOG.debug("[DiagnosticTestRequestService] CREATE - start. payload={} username={}", dto, username);
        facilityHelper.validateFacilityExists(dto.fromFacilityId());
        departmentHelper.validateDepartmentExists(dto.fromDepartmentId());
        DiagnosticTestRequest request = new DiagnosticTestRequest();
        request.setStatus(DiagnosticTestRequestStatus.REQUESTED);
        request.setType(dto.type());
        request.setName(dto.name());
        request.setIndication(dto.indication());
        request.setFromDepartmentId(dto.fromDepartmentId());
        request.setFromFacilityId(dto.fromFacilityId());

        DiagnosticTestRequest saved = repository.save(request);
        LOG.debug("[DiagnosticTestRequestService] CREATE - done. id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    public DiagnosticTestRequest update(DiagnosticTestRequestUpdateDTO dto, String username) {
        LOG.debug("[DiagnosticTestRequestService] UPDATE - start. id={} payload={} username={}", dto.id(), dto, username);
        DiagnosticTestRequest request = getDiagnosticTestRequestById(dto.id());

        ensureOwner(request, username, "update_not_allowed", "Only the creator can update this request");

        if (request.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "locked",
                    "diagnostic_test_requests",
                    "Cannot update a non-REQUESTED request"
            );
        }
        facilityHelper.validateFacilityExists(dto.fromFacilityId());
        departmentHelper.validateDepartmentExists(dto.fromDepartmentId());
        request.setType(dto.type());
        request.setName(dto.name());
        request.setIndication(dto.indication());
        request.setFromDepartmentId(dto.fromDepartmentId());
        request.setFromFacilityId(dto.fromFacilityId());

        DiagnosticTestRequest saved = repository.save(request);
        LOG.debug("[DiagnosticTestRequestService] UPDATE - done. id={} status={}", saved.getId(), saved.getStatus());
        return saved;
    }

    public DiagnosticTestRequest approve(Long id, String username) {
        LOG.debug("[DiagnosticTestRequestService] APPROVE - start. id={} username={}", id, username);
        DiagnosticTestRequest request = getDiagnosticTestRequestById(id);

        if (request.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_test_requests",
                    "Only REQUESTED requests can be approved"
            );
        }

        request.setStatus(DiagnosticTestRequestStatus.APPROVED);
        request.setApprovedBy(username);
        request.setApprovedDate(Instant.now());

        DiagnosticTestRequest saved = repository.save(request);
        LOG.debug("[DiagnosticTestRequestService] APPROVE - done. id={} status={} approvedBy={}",
                saved.getId(), saved.getStatus(), saved.getApprovedBy());
        return saved;
    }

    public DiagnosticTestRequest reject(Long id, String username, String rejectedReason) {
        LOG.debug("[DiagnosticTestRequestService] REJECT - start. id={} username={} reason={}", id, username, rejectedReason);
        DiagnosticTestRequest request = getDiagnosticTestRequestById(id);

        if (request.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_test_requests",
                    "Only REQUESTED requests can be rejected"
            );
        }

        request.setStatus(DiagnosticTestRequestStatus.REJECTED);
        request.setRejectedBy(username);
        request.setRejectedReason(rejectedReason);
        request.setRejectedDate(Instant.now());
        DiagnosticTestRequest saved = repository.save(request);
        LOG.debug("[DiagnosticTestRequestService] REJECT - done. id={} status={} rejectedBy={}",
                saved.getId(), saved.getStatus(), saved.getRejectedBy());
        return saved;
    }

    public DiagnosticTestRequest setDiagnosticTest(Long requestId, Long diagnosticTestId) {
        LOG.debug("[DiagnosticTestRequestService] SET_DIAGNOSTIC_TEST - start. requestId={} diagnosticTestId={}",
                requestId, diagnosticTestId);

        DiagnosticTestRequest request = getDiagnosticTestRequestById(requestId);

        if (request.getStatus() == DiagnosticTestRequestStatus.REJECTED) {
            throw new BadRequestAlertException(
                    "invalid_transition",
                    "diagnostic_test_requests",
                    "Cannot link a REJECTED request"
            );
        }

        if (request.getStatus() == DiagnosticTestRequestStatus.APPROVED && request.getDiagnosticTestId() != null) {
            throw new BadRequestAlertException(
                    "locked",
                    "diagnostic_test_requests",
                    "Cannot modify diagnostic test after approval"
            );
        }


        request.setDiagnosticTestId(diagnosticTestId);

        DiagnosticTestRequest saved = repository.save(request);
        LOG.debug("[DiagnosticTestRequestService] SET_DIAGNOSTIC_TEST - done. id={} status={} diagnosticTestId={}",
                saved.getId(), saved.getStatus(), saved.getDiagnosticTestId());
        return saved;
    }


    public void delete(Long id, String username) {
        LOG.debug("[DiagnosticTestRequestService] DELETE - start. id={} username={}", id, username);
        DiagnosticTestRequest request = getDiagnosticTestRequestById(id);

        ensureOwner(request, username, "delete_not_allowed", "Only the creator can delete this request");

        if (request.getStatus() != DiagnosticTestRequestStatus.REQUESTED) {
            throw new BadRequestAlertException(
                    "locked",
                    "diagnostic_test_requests",
                    "Cannot delete a non-REQUESTED request"
            );
        }

        repository.deleteById(id);
        LOG.debug("[DiagnosticTestRequestService] DELETE - done. id={}", id);
    }

    @Transactional(readOnly = true)
    public DiagnosticTestRequest getDiagnosticTestRequestById(Long id) {
        LOG.debug("[DiagnosticTestRequestService] GET - id={}", id);
        return repository.findById(id)
                .orElseThrow(() -> new BadRequestAlertException(
                        "notfound",
                        "diagnostic_test_requests",
                        "DiagnosticTestRequest not found with id " + id
                ));
    }

    private void ensureOwner(DiagnosticTestRequest request, String username, String errorKey, String message) {
        String owner = request.getCreatedBy();
        if (owner == null || !owner.equals(username)) {
            throw new BadRequestAlertException(errorKey, "diagnostic_test_requests", message);
        }
    }
}
