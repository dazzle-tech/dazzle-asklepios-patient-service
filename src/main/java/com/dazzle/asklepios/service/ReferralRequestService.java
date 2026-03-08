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
import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferralRequestService {

    private static final Logger LOG = LoggerFactory.getLogger(ReferralRequestService.class);

    private final ReferralRequestRepository referralRequestRepository;
    private final PatientRepository patientRepository;
    private final PatientEncounterRepository patientEncounterRepository;

    public ReferralRequest create(ReferralRequestCreateDTO dto) {
        LOG.info("[CREATE] ReferralRequest payload={}", dto);

        Patient patient = patientRepository.findById(dto.patientId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Patient not found with id " + dto.patientId(),
                        "referralRequest",
                        "patient.notfound"
                ));

        PatientEncounter encounter = Optional.ofNullable(dto.encounterId())
                .map(encounterId -> {
                    LOG.debug("[CREATE] Resolving encounter id={}", encounterId);
                    return patientEncounterRepository.findById(encounterId)
                            .orElseThrow(() -> new NotFoundAlertException(
                                    "PatientEncounter not found with id " + encounterId,
                                    "referralRequest",
                                    "encounter.notfound"
                            ));
                })
                .orElse(null);

        ReferralRequest entity = ReferralRequest.builder()
                .patient(patient)
                .encounter(encounter)
                .referralType(dto.referralType() != null ? dto.referralType() : ReferralType.INTERNAL)
                .fromFacilityId(dto.fromFacilityId())
                .toFacilityId(dto.toFacilityId())
                .fromDepartmentId(dto.fromDepartmentId())
                .toDepartmentId(dto.toDepartmentId())
                .referralReason(dto.referralReason())
                .priority(dto.priority())
                .status(ReferralStatus.REQUESTED)
                .build();

        try {
            ReferralRequest saved = referralRequestRepository.saveAndFlush(entity);
            LOG.info("[CREATE] Successfully created ReferralRequest id={}", saved.getId());
            return saved;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            throw handleConstraintViolation(ex);
        }
    }

    public Optional<ReferralRequest> update(Long id, ReferralRequestUpdateDTO dto) {
        Long targetId = id != null ? id : dto.id();
        LOG.info("[UPDATE] ReferralRequest id={} payload={}", targetId, dto);

        return referralRequestRepository.findById(targetId).map(entity -> {

            Patient patient = patientRepository.findById(dto.patientId())
                    .orElseThrow(() -> new NotFoundAlertException(
                            "Patient not found with id " + dto.patientId(),
                            "referralRequest",
                            "patient.notfound"
                    ));

            PatientEncounter encounter = Optional.ofNullable(dto.encounterId())
                    .map(encounterId -> {
                        LOG.debug("[UPDATE] Resolving encounter id={}", encounterId);
                        return patientEncounterRepository.findById(encounterId)
                                .orElseThrow(() -> new NotFoundAlertException(
                                        "PatientEncounter not found with id " + encounterId,
                                        "referralRequest",
                                        "encounter.notfound"
                                ));
                    })
                    .orElse(null);

            entity.setPatient(patient);
            entity.setEncounter(encounter);
            entity.setReferralType(dto.referralType());
            entity.setFromFacilityId(dto.fromFacilityId());
            entity.setToFacilityId(dto.toFacilityId());
            entity.setFromDepartmentId(dto.fromDepartmentId());
            entity.setToDepartmentId(dto.toDepartmentId());
            entity.setReferralReason(dto.referralReason());
            entity.setPriority(dto.priority());

            try {
                ReferralRequest updated = referralRequestRepository.saveAndFlush(entity);
                LOG.info("[UPDATE] Successfully updated ReferralRequest id={}", updated.getId());
                return updated;
            } catch (DataIntegrityViolationException | JpaSystemException ex) {
                throw handleConstraintViolation(ex);
            }
        });
    }

    public ReferralRequest accept(Long referralRequestId) {
        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "referralRequest",
                        "user.notfound"
                ));

        LOG.info("[ACCEPT] ReferralRequest id={} acceptedBy={}", referralRequestId, currentUser);

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
        referralRequest.setAcceptedBy(currentUser);

        try {
            ReferralRequest savedReferralRequest = referralRequestRepository.saveAndFlush(referralRequest);
            LOG.info("[ACCEPT] ReferralRequest success id={} acceptedBy={}", referralRequestId, currentUser);
            return savedReferralRequest;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[ACCEPT] ReferralRequest failed (constraint) id={} acceptedBy={}",
                    referralRequestId, currentUser, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[ACCEPT] ReferralRequest failed (unexpected) id={}", referralRequestId, ex);
            throw ex;
        }
    }

    public ReferralRequest reject(Long referralRequestId, String rejectReason) {
        String currentUser = SecurityUtils.getCurrentUserLogin()
                .orElseThrow(() -> new BadRequestAlertException(
                        "Current user not found",
                        "referralRequest",
                        "user.notfound"
                ));

        LOG.info("[REJECT] ReferralRequest id={} rejectedBy={} reason={}",
                referralRequestId, currentUser, rejectReason);

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
        referralRequest.setRejectedBy(currentUser);

        try {
            ReferralRequest savedReferralRequest = referralRequestRepository.saveAndFlush(referralRequest);
            LOG.info("[REJECT] ReferralRequest success id={} rejectedBy={}", referralRequestId, currentUser);
            return savedReferralRequest;
        } catch (DataIntegrityViolationException | JpaSystemException ex) {
            LOG.warn("[REJECT] ReferralRequest failed (constraint) id={} rejectedBy={}",
                    referralRequestId, currentUser, ex);
            throw handleConstraintViolation(ex);
        } catch (RuntimeException ex) {
            LOG.error("[REJECT] ReferralRequest failed (unexpected) id={}", referralRequestId, ex);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Page<ReferralRequest> getByEncounter(Long encounterId, Pageable pageable) {
        LOG.debug("[LIST] ReferralRequests by encounterId={} pageable={}", encounterId, pageable);

        Page<ReferralRequest> page = referralRequestRepository.findByEncounter_Id(encounterId, pageable);

        LOG.debug("[LIST] ReferralRequests by encounterId={} result size={} total={}",
                encounterId, page.getNumberOfElements(), page.getTotalElements());

        return page;
    }

    @Transactional(readOnly = true)
    public Page<ReferralRequest> getByToFacilityAndCreatedDateRange(
            Long toFacilityId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        LOG.debug("[LIST] ReferralRequests by toFacilityId={} from={} to={}", toFacilityId, from, to);
        return referralRequestRepository.findByToFacilityIdAndCreatedDateBetween(toFacilityId, from, to, pageable);
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

        if (messageLower.contains("fk_referral_request_encounter")) {
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