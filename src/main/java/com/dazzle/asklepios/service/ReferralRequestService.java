package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.ReferralRequest;
import com.dazzle.asklepios.domain.enumeration.ReferralStatus;
import com.dazzle.asklepios.domain.enumeration.ReferralType;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.repository.ReferralRequestRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.referralRequest.ReferralRequestCreateDTO;
import com.dazzle.asklepios.service.dto.referralRequest.ReferralRequestUpdateDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.FacilityHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferralRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(ReferralRequestService.class);

    private final ReferralRequestRepository referralRequestRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final FacilityHelper facilityHelper;
    private final DepartmentHelper departmentHelper;

    public ReferralRequest createReferralRequest(ReferralRequestCreateDTO createDto) {
        LOG.info("[CREATE] ReferralRequest payload={}", createDto);

        Patient patient = patientRepository.findById(createDto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + createDto.patientId(),
                        "referralRequest",
                        "patient.notfound"
                ));

        PatientEncounter encounter = findEncounterByIdOrThrow(createDto.encounterId(), "CREATE");
        facilityHelper.validateFacilityExists(createDto.fromFacilityId());
        facilityHelper.validateFacilityExists(createDto.toFacilityId());
        departmentHelper.validateDepartmentExists(createDto.fromDepartmentId());
        departmentHelper.validateDepartmentExists(createDto.toDepartmentId());

        ReferralRequest referralRequest = ReferralRequest.builder()
                .patient(patient)
                .encounter(encounter)
                .referralType(createDto.referralType() != null ? createDto.referralType() : ReferralType.INTERNAL)
                .fromFacilityId(createDto.fromFacilityId())
                .toFacilityId(createDto.toFacilityId())
                .fromDepartmentId(createDto.fromDepartmentId())
                .toDepartmentId(createDto.toDepartmentId())
                .referralReason(createDto.referralReason())
                .priority(createDto.priority())
                .status(ReferralStatus.REQUESTED)
                .build();

        try {
            ReferralRequest savedReferralRequest = referralRequestRepository.saveAndFlush(referralRequest);
            LOG.info("[CREATE] Successfully created ReferralRequest id={}", savedReferralRequest.getId());
            return savedReferralRequest;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public java.util.Optional<ReferralRequest> updateReferralRequest(Long referralRequestId, ReferralRequestUpdateDTO updateDto) {
        Long targetReferralRequestId = referralRequestId != null ? referralRequestId : updateDto.id();
        LOG.info("[UPDATE] ReferralRequest id={} payload={}", targetReferralRequestId, updateDto);

        return referralRequestRepository.findById(targetReferralRequestId).map(existingReferralRequest -> {

            Patient patient = patientRepository.findById(updateDto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + updateDto.patientId(),
                            "referralRequest",
                            "patient.notfound"
                    ));

            PatientEncounter encounter = findEncounterByIdOrThrow(updateDto.encounterId(), "UPDATE");

            facilityHelper.validateFacilityExists(updateDto.fromFacilityId());
            facilityHelper.validateFacilityExists(updateDto.toFacilityId());
            departmentHelper.validateDepartmentExists(updateDto.fromDepartmentId());
            departmentHelper.validateDepartmentExists(updateDto.toDepartmentId());

            existingReferralRequest.setPatient(patient);
            existingReferralRequest.setEncounter(encounter);
            existingReferralRequest.setReferralType(updateDto.referralType());
            existingReferralRequest.setFromFacilityId(updateDto.fromFacilityId());
            existingReferralRequest.setToFacilityId(updateDto.toFacilityId());
            existingReferralRequest.setFromDepartmentId(updateDto.fromDepartmentId());
            existingReferralRequest.setToDepartmentId(updateDto.toDepartmentId());
            existingReferralRequest.setReferralReason(updateDto.referralReason());
            existingReferralRequest.setPriority(updateDto.priority());

            try {
                ReferralRequest updatedReferralRequest = referralRequestRepository.saveAndFlush(existingReferralRequest);
                LOG.info("[UPDATE] Successfully updated ReferralRequest id={}", updatedReferralRequest.getId());
                return updatedReferralRequest;
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    public ReferralRequest acceptReferralRequest(Long referralRequestId) {
        String currentUsername = getCurrentUsername();

        LOG.info("[ACCEPT] ReferralRequest id={} acceptedBy={}", referralRequestId, currentUsername);

        ReferralRequest referralRequest = referralRequestRepository.findById(referralRequestId)
                .orElseThrow(() -> {
                    LOG.warn("[ACCEPT] ReferralRequest not found id={}", referralRequestId);
                    return new NotFoundAlertException(
                            "ReferralRequest not found with id " + referralRequestId,
                            "referralRequest",
                            "notfound"
                    );
                });

        referralRequest.setStatus(ReferralStatus.ACCEPTED);
        referralRequest.setAcceptedDate(Instant.now());
        referralRequest.setAcceptedBy(currentUsername);

        try {
            ReferralRequest savedReferralRequest = referralRequestRepository.saveAndFlush(referralRequest);
            LOG.info("[ACCEPT] ReferralRequest success id={} acceptedBy={}", referralRequestId, currentUsername);
            return savedReferralRequest;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[ACCEPT] ReferralRequest failed (constraint) id={} acceptedBy={}",
                    referralRequestId, currentUsername, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[ACCEPT] ReferralRequest failed (unexpected) id={}", referralRequestId, ex);
            throw ex;
        }
    }

    public ReferralRequest rejectReferralRequest(Long referralRequestId, String rejectReason) {
        String currentUsername = getCurrentUsername();

        LOG.info("[REJECT] ReferralRequest id={} rejectedBy={} reason={}",
                referralRequestId, currentUsername, rejectReason);

        ReferralRequest referralRequest = referralRequestRepository.findById(referralRequestId)
                .orElseThrow(() -> {
                    LOG.warn("[REJECT] ReferralRequest not found id={}", referralRequestId);
                    return new NotFoundAlertException(
                            "ReferralRequest not found with id " + referralRequestId,
                            "referralRequest",
                            "notfound"
                    );
                });

        referralRequest.setStatus(ReferralStatus.REJECTED);
        referralRequest.setRejectReason(rejectReason);
        referralRequest.setRejectedDate(Instant.now());
        referralRequest.setRejectedBy(currentUsername);

        try {
            ReferralRequest savedReferralRequest = referralRequestRepository.saveAndFlush(referralRequest);
            LOG.info("[REJECT] ReferralRequest success id={} rejectedBy={}", referralRequestId, currentUsername);
            return savedReferralRequest;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[REJECT] ReferralRequest failed (constraint) id={} rejectedBy={}",
                    referralRequestId, currentUsername, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[REJECT] ReferralRequest failed (unexpected) id={}", referralRequestId, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<ReferralRequest> getReferralRequestsByEncounter(Long encounterId, Pageable pageable) {
        LOG.debug("[LIST] ReferralRequests by encounterId={} pageable={}", encounterId, pageable);

        Page<ReferralRequest> referralRequestPage =
                referralRequestRepository.findByEncounter_Id(encounterId, pageable);

        LOG.debug("[LIST] ReferralRequests by encounterId={} result size={} total={}",
                encounterId,
                referralRequestPage.getNumberOfElements(),
                referralRequestPage.getTotalElements());

        return referralRequestPage;
    }

    @Transactional(readOnly = true)
    public Page<ReferralRequest> getReferralRequestsByToFacilityAndCreatedDateRange(
            Long toFacilityId,
            Instant fromDateTime,
            Instant toDateTime,
            Pageable pageable
    ) {
        LOG.debug("[LIST] ReferralRequests by toFacilityId={} from={} to={}",
                toFacilityId, fromDateTime, toDateTime);

        return referralRequestRepository.findByToFacilityIdAndCreatedDateBetween(
                toFacilityId,
                fromDateTime,
                toDateTime,
                pageable
        );
    }

    private String getCurrentUsername() {
        return SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "referralRequest",
                        "user.notfound"
                ));
    }

    private PatientEncounter findEncounterByIdOrThrow(Long encounterId, String operationName) {
        LOG.debug("[{}] Resolving encounter id={}", operationName, encounterId);

        return patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "PatientEncounter not found with id " + encounterId,
                        "referralRequest",
                        "encounter.notfound"
                ));
    }

    private RuntimeException handleConstraintViolation(Exception exception) {
        Throwable root = getRootCause(exception);
        String message = root != null ? root.getMessage() : exception.getMessage();
        String messageLower = message != null ? message.toLowerCase() : "";

        LOG.warn("[DB_CONSTRAINT] ReferralRequest constraint violated rootMessage={}", message, exception);

        if (messageLower.contains("uk_referral_encounter_to_department")) {
            return new BadRequestAlertException(
                    "A referral to the same department already exists for this encounter.",
                    "referralRequest",
                    "encounter.department.duplicate"
            );
        }

        if (messageLower.contains("ck_referral_requests_reject_reason")) {
            return new BadRequestAlertException(
                    "Reject reason is required when status is REJECTED.",
                    "referralRequest",
                    "rejectReason.required"
            );
        }

        if (messageLower.contains("ck_referral_requests_accepted_fields")) {
            return new BadRequestAlertException(
                    "Accepted date and accepted by are required when status is ACCEPTED.",
                    "referralRequest",
                    "acceptedFields.required"
            );
        }

        if (messageLower.contains("fk_referral_request_patient")) {
            return new BadRequestAlertException(
                    "Invalid patient id.",
                    "referralRequest",
                    "patient.invalid"
            );
        }

        if (messageLower.contains("fk_referral_encounter")
                || messageLower.contains("fk_referral_request_encounter")) {
            return new BadRequestAlertException(
                    "Invalid encounter id.",
                    "referralRequest",
                    "encounter.invalid"
            );
        }

        return new BadRequestAlertException(
                "Database constraint violated while saving referral request.",
                "referralRequest",
                "db.constraint"
        );
    }
}
