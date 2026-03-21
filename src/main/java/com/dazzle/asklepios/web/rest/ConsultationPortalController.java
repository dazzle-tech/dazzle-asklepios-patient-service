package com.dazzle.asklepios.web.rest;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.service.ConsultationPortalService;
import com.dazzle.asklepios.service.ConsultationService;
import com.dazzle.asklepios.service.dto.consultation.ConsultationRejectDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationResponseDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitRequestDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitResultDTO;
import com.dazzle.asklepios.web.rest.Helper.PaginationUtil;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/patient")
public class ConsultationPortalController {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsultationPortalController.class);

    private final ConsultationPortalService consultationPortalService;
    private final ConsultationService consultationService;

    public ConsultationPortalController(
            ConsultationPortalService consultationPortalService,
            ConsultationService consultationService
    ) {
        this.consultationPortalService = consultationPortalService;
        this.consultationService = consultationService;
        LOG.debug(
                "ConsultationPortalController initialized consultationPortalService={} consultationService={}",
                consultationPortalService, consultationService
        );
    }

    @GetMapping("/consultation-portal/search")
    public ResponseEntity<List<Consultation>> searchConsultations(
            @RequestParam @NotNull Instant fromDate,
            @RequestParam @NotNull Instant toDate,
            @RequestParam @NotNull Long fromFacilityId,
            @RequestParam(required = false) Long practitionerId,
            @RequestParam(required = false) Long toDepartmentId,
            @RequestParam(required = false) List<Long> fromDepartmentIds,
            @ParameterObject Pageable pageable,
            @RequestParam(defaultValue = "false") boolean showRejected
    ) {
        LOG.debug(
                "REST SEARCH Consultations fromDate={} toDate={} fromFacilityId={} practitionerId={} toDepartmentId={} fromDepartmentIds={} pageable={} showRejected={}",
                fromDate, toDate, fromFacilityId, practitionerId, toDepartmentId, fromDepartmentIds, pageable, showRejected
        );

        if (fromDate.isAfter(toDate)) {
            throw new BadRequestAlertException(
                    "fromDate must be before or equal to toDate", "consultation", "date.invalid.range");
        }

        if (practitionerId == null && toDepartmentId == null) {
            throw new BadRequestAlertException(
                    "Either practitionerId or toDepartmentId must be provided",
                    "consultation", "search.filter.required");
        }

        if (practitionerId == null)
            LOG.debug("REST SEARCH Consultations - practitionerId is null, will fetch department consultations only");

        if (toDepartmentId == null)
            LOG.debug("REST SEARCH Consultations - toDepartmentId is null, will fetch practitioner consultations only");

        Page<Consultation> page = consultationPortalService.listPractitionerAndDepartmentConsultations(
                fromDate, toDate, fromFacilityId, practitionerId,
                toDepartmentId, fromDepartmentIds, pageable, showRejected
        );

        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
                ServletUriComponentsBuilder.fromCurrentRequest(), page);

        LOG.info("REST SEARCH Consultations - Retrieved {} consultations", page.getContent().size());
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    @PutMapping("/consultation/{id}/confirm")
    public ResponseEntity<Consultation> confirmConsultation(@PathVariable Long id) {
        LOG.debug("REST confirm Consultation id={}", id);

        if (id == null) {
            LOG.warn("REST confirm Consultation - id is missing");
            throw new BadRequestAlertException(
                    "Consultation id is required", "consultation", "id.required");
        }

        Consultation existing = consultationService.findById(id);

        if (existing.getStatus() != ConsultationStatus.REQUESTED) {
            LOG.warn("REST confirm Consultation - Not allowed for status={} id={}", existing.getStatus(), id);
            throw new BadRequestAlertException(
                    "Consultation can be confirmed only when status is REQUESTED",
                    "consultation", "confirm.only.requested");
        }

        Consultation confirmed = consultationPortalService.confirmConsultation(id);
        LOG.info("REST confirm Consultation - Successfully confirmed consultation id={}", confirmed.getId());
        return ResponseEntity.ok(confirmed);
    }

    @PutMapping("/consultation/{id}/reject")
    public ResponseEntity<Consultation> rejectConsultation(
            @PathVariable @NotNull Long id,
            @Valid @RequestBody ConsultationRejectDTO consultationRejectDTO
    ) {
        LOG.debug("REST reject Consultation payload id={} dto={}", id, consultationRejectDTO);

        if (consultationRejectDTO == null) {
            LOG.warn("REST reject Consultation - request body is missing id={}", id);
            throw new BadRequestAlertException(
                    "Request body is required", "consultation", "body.required");
        }

        Consultation existing = consultationService.findById(id);

        if (existing.getStatus() != ConsultationStatus.REQUESTED) {
            LOG.warn("REST reject Consultation - Not allowed for status={} id={}", existing.getStatus(), id);
            throw new BadRequestAlertException(
                    "Consultation can be rejected only when status is REQUESTED",
                    "consultation", "reject.only.requested");
        }

        Consultation rejected = consultationPortalService.rejectConsultation(id, consultationRejectDTO);
        LOG.info("REST reject Consultation - Successfully rejected consultation id={}", rejected.getId());
        return ResponseEntity.ok(rejected);
    }

    @PutMapping("/consultation/{id}/response")
    public ResponseEntity<Consultation> submitConsultationResponse(
            @PathVariable Long id,
            @Valid @RequestBody ConsultationResponseDTO dto
    ) {
        LOG.debug("REST submit Consultation response payload id={} dto={}", id, dto);

        if (id == null) {
            LOG.warn("REST submit Consultation response - id is missing");
            throw new BadRequestAlertException(
                    "Consultation id is required", "consultation", "id.required");
        }

        if (dto == null) {
            LOG.warn("REST submit Consultation response - request body is missing id={}", id);
            throw new BadRequestAlertException(
                    "Request body is required", "consultation", "body.required");
        }

        Consultation existing = consultationService.findById(id);

        if (existing.getStatus() != ConsultationStatus.CONFIRMED && existing.getStatus()!= ConsultationStatus.READY) {
            LOG.warn("REST submit Consultation response - Not allowed for status={} id={}", existing.getStatus(), id);
            throw new BadRequestAlertException(
                    "Consultation response can be submitted only when status is CONFIRMED or ready",
                    "consultation", "response.only.confirmed");
        }

        Consultation updated = consultationPortalService.submitConsultationResponse(id, dto);
        LOG.info("REST submit Consultation response - Successfully added response to consultation id={}", updated.getId());
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/consultation/submit")
    public ResponseEntity<ConsultationSubmitResultDTO> submitConsultations(
            @Valid @RequestBody ConsultationSubmitRequestDTO dto
    ) {
        LOG.debug("REST submit consultations payload={}", dto);

        if (dto == null) {
            LOG.warn("REST submit consultations - request body is missing");
            throw new BadRequestAlertException(
                    "Request body is required", "consultation", "body.required");
        }

        if (dto.consultationIds() == null || dto.consultationIds().isEmpty()) {
            LOG.warn("REST submit consultations - consultationIds is empty");
            throw new BadRequestAlertException(
                    "At least one consultation id is required", "consultation", "ids.required");
        }

        ConsultationSubmitResultDTO result = consultationPortalService.submitConsultations(dto);
        LOG.info("REST submit consultations - Successfully submitted {} consultations with {} errors",
                result.submittedCount(), result.errors().size());
        return ResponseEntity.ok(result);
    }
}