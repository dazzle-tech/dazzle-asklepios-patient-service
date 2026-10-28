package com.dazzle.asklepios.service;

import com.dazzle.asklepios.attachments.AttachmentProperties;
import com.dazzle.asklepios.client.setup.FacilityClient;
import com.dazzle.asklepios.client.setup.UserClient;
import com.dazzle.asklepios.client.setup.UserRoleClient;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityUserDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientComplaint;
import com.dazzle.asklepios.domain.PatientComplaintAction;
import com.dazzle.asklepios.domain.PatientComplaintAttachment;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.enumeration.DocumentType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintActionStatus;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintFeedbackType;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintPreferredContactMethod;
import com.dazzle.asklepios.domain.enumeration.PatientComplaintStatus;
import com.dazzle.asklepios.repository.PatientComplaintRepository;
import com.dazzle.asklepios.repository.PatientComplaintSpecs;
import com.dazzle.asklepios.repository.PatientDocumentRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import com.dazzle.asklepios.security.AuthoritiesConstants;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintActionCompleteDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintActionCreateDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintAssignDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintDeadlineDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintMobileSubmitDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintReopenDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintResolveDTO;
import com.dazzle.asklepios.service.dto.patientComplaint.PatientComplaintSubmitDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintActionVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintAttachmentDownloadVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintAttachmentVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintDetailVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintListVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintSubmitResponseVM;
import com.dazzle.asklepios.web.rest.vm.PatientComplaintSummaryVM;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PatientComplaintService {

    private static final String ENTITY_NAME = "patientComplaint";
    private static final String ATTACHMENT_ENTITY = "patientComplaintAttachment";
    /**
     * Business calendar zone used across appointment/job scheduling in this service
     * ({@code Asia/Hebron} / {@code Asia/Gaza}). Visit-date "not in the future" checks
     * use this zone so the calendar day the patient selected is preserved near UTC midnight.
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Hebron");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s().-]{6,50}$");
    private static final DateTimeFormatter YYYY = DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter MM = DateTimeFormatter.ofPattern("MM").withZone(ZoneOffset.UTC);

    private final PatientComplaintRepository patientComplaintRepository;
    private final PatientRepository patientRepository;
    private final PatientDocumentRepository patientDocumentRepository;
    private final FacilityClient facilityClient;
    private final DepartmentHelper departmentHelper;
    private final UserClient userClient;
    private final UserRoleClient userRoleClient;
    private final AttachmentProperties attachmentProperties;
    private final AttachmentStorageService attachmentStorageService;

    public PatientComplaintSubmitResponseVM submit(PatientComplaintSubmitDTO dto, List<MultipartFile> files) {
        log.debug("Public submit patient complaint for facilityId={}", dto.getFacilityId());

        normalizeSubmitDto(dto);
        validateConsent(dto);
        validateSubmitIdentity(dto);
        validateVisitFields(dto);
        validateOptionalPhone(dto.getContactPhone());

        // Optional patient JWT (portal OTP): link only the authenticated patient; never from client patientId.
        return persistNewComplaint(dto, resolveOptionalLinkedPatientFromJwt(dto), files);
    }

    /**
     * When a PATIENT JWT is present on the public web submit, persist that Patient relationship.
     * Skip for anonymous or on-behalf submissions (verified identity must not attach to another person).
     */
    private Patient resolveOptionalLinkedPatientFromJwt(PatientComplaintSubmitDTO dto) {
        if (Boolean.TRUE.equals(dto.getAnonymous()) || Boolean.TRUE.equals(dto.getOnBehalf())) {
            return null;
        }
        if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.PATIENT)) {
            return null;
        }
        return requireAuthenticatedPatient();
    }

    public PatientComplaintSubmitResponseVM submitMobile(
        PatientComplaintMobileSubmitDTO mobileDto,
        List<MultipartFile> files
    ) {
        Patient patient = requireAuthenticatedPatient();
        PatientComplaintSubmitDTO dto = toSubmitDtoFromAuthenticatedPatient(mobileDto, patient);

        normalizeSubmitDto(dto);
        validateConsent(dto);
        validateSubmitIdentity(dto);
        validateVisitFields(dto);
        validateOptionalPhone(dto.getContactPhone());

        return persistNewComplaint(dto, patient, files);
    }

    private PatientComplaintSubmitResponseVM persistNewComplaint(
        PatientComplaintSubmitDTO dto,
        Patient linkedPatient,
        List<MultipartFile> files
    ) {
        FacilityDTO facility = requireActiveFacility(dto.getFacilityId());
        String departmentName = requireActiveDepartmentForFacility(dto.getDepartmentId(), dto.getFacilityId());

        Instant now = Instant.now();
        PatientComplaint complaint = new PatientComplaint();
        complaint.setComplaintReference(generateReference());
        complaint.setAnonymous(Boolean.TRUE.equals(dto.getAnonymous()));
        complaint.setConsentToContact(Boolean.TRUE.equals(dto.getConsentToContact()));
        complaint.setPatient(linkedPatient);
        if (complaint.isAnonymous()) {
            complaint.setPatientName(null);
            complaint.setPatientIdentifier(null);
            complaint.setDocumentType(null);
            complaint.setDocumentNumber(null);
            complaint.setContactPhone(null);
            complaint.setContactEmail(null);
            complaint.setPreferredContactMethod(null);
            complaint.setOnBehalf(null);
            complaint.setRelationshipToPatient(null);
        } else {
            complaint.setPatientName(dto.getPatientName().trim());
            complaint.setDocumentType(dto.getDocumentType());
            complaint.setDocumentNumber(dto.getDocumentNumber());
            // Keep legacy identifier searchable: prefer document number when provided.
            complaint.setPatientIdentifier(
                dto.getDocumentNumber() != null ? dto.getDocumentNumber() : dto.getPatientIdentifier()
            );
            complaint.setContactPhone(dto.getContactPhone());
            complaint.setContactEmail(dto.getContactEmail());
            complaint.setPreferredContactMethod(dto.getPreferredContactMethod());
            complaint.setOnBehalf(dto.getOnBehalf());
            complaint.setRelationshipToPatient(
                Boolean.TRUE.equals(dto.getOnBehalf()) ? dto.getRelationshipToPatient() : null
            );
        }
        complaint.setFacilityId(facility.id());
        complaint.setFacilityName(facility.name());
        complaint.setDepartmentId(dto.getDepartmentId());
        complaint.setDepartmentName(departmentName);
        complaint.setFeedbackType(dto.getFeedbackType());
        complaint.setComplaintType(dto.getComplaintType());
        complaint.setIncidentDate(dto.getIncidentDate());
        complaint.setIncidentTime(dto.getIncidentTime());
        // Subject is retained for staff list/search; derived from feedback type (not echoed as patient input).
        complaint.setSubject(subjectFromFeedbackType(dto.getFeedbackType()));
        complaint.setDescription(dto.getDescription().trim());
        complaint.setInvolvedStaffNames(blankToNull(dto.getInvolvedStaffNames()));
        complaint.setDesiredResolution(blankToNull(dto.getDesiredResolution()));
        complaint.setReceivedDate(now);
        complaint.setStatus(PatientComplaintStatus.NEW);

        PatientComplaint saved = patientComplaintRepository.save(complaint);
        storeAttachments(saved, files == null ? List.of() : files, now);
        PatientComplaint persisted = patientComplaintRepository.save(saved);

        PatientComplaintSubmitResponseVM response = new PatientComplaintSubmitResponseVM();
        response.setReference(persisted.getComplaintReference());
        response.setReceivedDate(persisted.getReceivedDate());
        return response;
    }

    private Patient requireAuthenticatedPatient() {
        if (!SecurityUtils.hasCurrentUserThisAuthority(AuthoritiesConstants.PATIENT)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Patient authority required");
        }
        String mrn = SecurityUtils
            .getCurrentUserLogin()
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElseThrow(() ->
                new BadRequestAlertException(
                    "Authenticated patient identity is required",
                    ENTITY_NAME,
                    "identity.required"
                )
            );
        return patientRepository
            .findByMedicalRecordNumber(mrn)
            .orElseThrow(() ->
                new NotFoundAlertException(
                    "Patient not found for authenticated medical record number",
                    "patient",
                    "notfound"
                )
            );
    }

    private PatientComplaintSubmitDTO toSubmitDtoFromAuthenticatedPatient(
        PatientComplaintMobileSubmitDTO mobileDto,
        Patient patient
    ) {
        String patientName = formatPatientDisplayName(patient);
        if (patientName == null) {
            throw new BadRequestAlertException(
                "Patient record is missing a display name",
                ENTITY_NAME,
                "patientName.required"
            );
        }
        String contactPhone = blankToNull(patient.getPrimaryMobileNumber());
        if (contactPhone == null) {
            throw new BadRequestAlertException(
                "Patient record is missing a contact phone number",
                ENTITY_NAME,
                "contactPhone.required"
            );
        }

        PatientDocument primaryDocument = patientDocumentRepository
            .findFirstByPatient_IdAndIsPrimaryTrue(patient.getId())
            .or(() -> patientDocumentRepository.findFirstByPatient_IdOrderByIdAsc(patient.getId()))
            .orElse(null);

        DocumentType documentType = primaryDocument != null ? primaryDocument.getType() : null;
        String documentNumber = primaryDocument != null
            ? blankToNull(primaryDocument.getNumber())
            : blankToNull(patient.getDocumentId());

        PatientComplaintSubmitDTO dto = new PatientComplaintSubmitDTO();
        dto.setAnonymous(false);
        dto.setOnBehalf(false);
        dto.setPatientName(patientName);
        dto.setDocumentType(documentType);
        dto.setDocumentNumber(documentNumber);
        dto.setPatientIdentifier(documentNumber != null ? documentNumber : patient.getMedicalRecordNumber());
        dto.setContactPhone(contactPhone);
        dto.setContactEmail(blankToNull(patient.getEmail()));
        dto.setPreferredContactMethod(mobileDto.getPreferredContactMethod());
        dto.setConsentToContact(mobileDto.getConsentToContact());
        dto.setFacilityId(mobileDto.getFacilityId());
        dto.setDepartmentId(mobileDto.getDepartmentId());
        dto.setFeedbackType(mobileDto.getFeedbackType());
        dto.setComplaintType(mobileDto.getComplaintType());
        dto.setIncidentDate(mobileDto.getIncidentDate());
        dto.setIncidentTime(mobileDto.getIncidentTime());
        dto.setDescription(mobileDto.getDescription());
        dto.setInvolvedStaffNames(mobileDto.getInvolvedStaffNames());
        dto.setDesiredResolution(mobileDto.getDesiredResolution());
        return dto;
    }

    @Transactional(readOnly = true)
    public PatientComplaintAttachmentDownloadVM getAttachmentDownloadUrl(Long complaintId, Long attachmentId) {
        PatientComplaint complaint = getAccessibleComplaintWithActions(complaintId);
        PatientComplaintAttachment attachment = complaint
            .getAttachments()
            .stream()
            .filter(item -> attachmentId.equals(item.getId()) && item.getDeletedAt() == null)
            .findFirst()
            .orElseThrow(() ->
                new NotFoundAlertException(
                    "Attachment not found: " + attachmentId,
                    ATTACHMENT_ENTITY,
                    "notfound"
                )
            );

        var presigned = attachmentStorageService.presignGet(attachment.getSpaceKey(), attachment.getFilename());
        return new PatientComplaintAttachmentDownloadVM(
            presigned.url().toString(),
            attachmentProperties.getPresignExpirySeconds()
        );
    }

    @Transactional(readOnly = true)
    public Page<PatientComplaintListVM> search(
        String search,
        PatientComplaintStatus status,
        Long assignedUserId,
        Instant receivedFrom,
        Instant receivedTo,
        String summaryFilter,
        Pageable pageable
    ) {
        Long facilityId = requireCurrentFacilityId();
        Long currentUserId = resolveCurrentUserIdOrNull();
        Instant now = Instant.now();

        Specification<PatientComplaint> spec = PatientComplaintSpecs.withFilters(
            facilityId,
            search,
            status,
            assignedUserId,
            receivedFrom,
            receivedTo,
            summaryFilter,
            currentUserId,
            now
        );

        return patientComplaintRepository.findAll(spec, pageable).map(complaint -> toListVM(complaint, now));
    }

    @Transactional(readOnly = true)
    public PatientComplaintSummaryVM summary() {
        Long facilityId = requireCurrentFacilityId();
        Long currentUserId = resolveCurrentUserIdOrNull();
        Instant now = Instant.now();

        PatientComplaintSummaryVM vm = new PatientComplaintSummaryVM();
        vm.setNewCount(patientComplaintRepository.countByFacilityIdAndStatus(facilityId, PatientComplaintStatus.NEW));
        vm.setUnassignedCount(patientComplaintRepository.countByFacilityIdAndAssignedUserIdIsNull(facilityId));
        vm.setAssignedToMeCount(
            currentUserId == null
                ? 0L
                : patientComplaintRepository.countByFacilityIdAndAssignedUserId(facilityId, currentUserId)
        );
        vm.setOverdueCount(
            patientComplaintRepository.countByFacilityIdAndTargetDeadlineIsNotNullAndTargetDeadlineBeforeAndStatusNotIn(
                facilityId,
                now,
                List.of(PatientComplaintStatus.RESOLVED, PatientComplaintStatus.CLOSED)
            )
        );
        vm.setResolvedCount(
            patientComplaintRepository.countByFacilityIdAndStatus(facilityId, PatientComplaintStatus.RESOLVED)
        );
        return vm;
    }

    @Transactional(readOnly = true)
    public PatientComplaintDetailVM getDetail(Long id) {
        PatientComplaint complaint = getAccessibleComplaintWithActions(id);
        return toDetailVM(complaint, Instant.now());
    }

    public PatientComplaintDetailVM assign(Long id, PatientComplaintAssignDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);

        UserDTO user = requireAssigneeInFacility(dto.getAssignedUserId(), complaint.getFacilityId());
        complaint.setAssignedUserId(user.id());
        complaint.setAssignedUserName(formatUserName(user));

        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM setDeadline(Long id, PatientComplaintDeadlineDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);
        complaint.setTargetDeadline(dto.getTargetDeadline());
        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM startHandling(Long id) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);

        if (complaint.getAssignedUserId() == null) {
            throw new BadRequestAlertException(
                "Assign an owner before starting handling",
                ENTITY_NAME,
                "owner.required"
            );
        }
        if (complaint.getStatus() != PatientComplaintStatus.NEW) {
            throw new BadRequestAlertException(
                "Handling can only be started from NEW status",
                ENTITY_NAME,
                "invalidstatus"
            );
        }

        complaint.setStatus(PatientComplaintStatus.IN_PROGRESS);
        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM addAction(Long id, PatientComplaintActionCreateDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);

        UserDTO user = requireAssigneeInFacility(dto.getAssignedUserId(), complaint.getFacilityId());
        PatientComplaintAction action = new PatientComplaintAction();
        action.setDescription(dto.getDescription().trim());
        action.setAssignedUserId(user.id());
        action.setAssignedUserName(formatUserName(user));
        action.setDeadline(dto.getDeadline());
        action.setStatus(PatientComplaintActionStatus.TO_DO);
        complaint.addAction(action);

        if (complaint.getStatus() == PatientComplaintStatus.NEW) {
            complaint.setStatus(PatientComplaintStatus.IN_PROGRESS);
        }

        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM startAction(Long id, Long actionId) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);

        PatientComplaintAction action = requireAction(complaint, actionId);
        if (action.getStatus() != PatientComplaintActionStatus.TO_DO) {
            throw new BadRequestAlertException(
                "Only TO_DO actions can be started",
                ENTITY_NAME,
                "invalidactionstatus"
            );
        }

        action.setStatus(PatientComplaintActionStatus.IN_PROGRESS);
        if (complaint.getStatus() == PatientComplaintStatus.NEW) {
            complaint.setStatus(PatientComplaintStatus.IN_PROGRESS);
        }

        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM completeAction(Long id, Long actionId, PatientComplaintActionCompleteDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);
        assertModifiable(complaint);

        PatientComplaintAction action = requireAction(complaint, actionId);
        if (
            action.getStatus() != PatientComplaintActionStatus.TO_DO &&
            action.getStatus() != PatientComplaintActionStatus.IN_PROGRESS
        ) {
            throw new BadRequestAlertException(
                "Only incomplete actions can be completed",
                ENTITY_NAME,
                "invalidactionstatus"
            );
        }

        String login = SecurityUtils
            .getCurrentUserLogin()
            .orElseThrow(() ->
                new BadRequestAlertException("Authenticated user is required", ENTITY_NAME, "user.required")
            );
        UserDTO currentUser = requireUserByLogin(login);

        action.setStatus(PatientComplaintActionStatus.DONE);
        action.setResult(dto.getResult().trim());
        action.setCompletedByUserId(currentUser.id());
        action.setCompletedBy(formatUserName(currentUser));
        action.setCompletedAt(Instant.now());

        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM resolve(Long id, PatientComplaintResolveDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);

        if (complaint.getStatus() == PatientComplaintStatus.CLOSED) {
            throw new BadRequestAlertException(
                "Closed complaints must be reopened before resolving",
                ENTITY_NAME,
                "invalidstatus"
            );
        }
        if (complaint.getStatus() == PatientComplaintStatus.RESOLVED) {
            throw new BadRequestAlertException("Complaint is already resolved", ENTITY_NAME, "invalidstatus");
        }
        if (complaint.getAssignedUserId() == null) {
            throw new BadRequestAlertException(
                "Assign an owner before resolving",
                ENTITY_NAME,
                "owner.required"
            );
        }
        if (hasIncompleteActions(complaint)) {
            throw new BadRequestAlertException(
                "Complete all actions before resolving",
                ENTITY_NAME,
                "incomplete.actions"
            );
        }

        complaint.setResolutionSummary(dto.getResolutionSummary().trim());
        complaint.setStatus(PatientComplaintStatus.RESOLVED);
        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM close(Long id) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);

        if (complaint.getStatus() != PatientComplaintStatus.RESOLVED) {
            throw new BadRequestAlertException(
                "Only a RESOLVED complaint can be closed",
                ENTITY_NAME,
                "invalidstatus"
            );
        }

        complaint.setStatus(PatientComplaintStatus.CLOSED);
        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    public PatientComplaintDetailVM reopen(Long id, PatientComplaintReopenDTO dto) {
        PatientComplaint complaint = getAccessibleComplaintForUpdateWithActions(id);

        if (
            complaint.getStatus() != PatientComplaintStatus.RESOLVED &&
            complaint.getStatus() != PatientComplaintStatus.CLOSED
        ) {
            throw new BadRequestAlertException(
                "Only RESOLVED or CLOSED complaints can be reopened",
                ENTITY_NAME,
                "invalidstatus"
            );
        }

        // Preserve existing resolutionSummary; only record reopen reason and return to IN_PROGRESS.
        complaint.setReopenReason(dto.getReopenReason().trim());
        complaint.setStatus(PatientComplaintStatus.IN_PROGRESS);
        return toDetailVM(patientComplaintRepository.save(complaint), Instant.now());
    }

    private FacilityDTO requireActiveFacility(Long facilityId) {
        if (facilityId == null) {
            throw new BadRequestAlertException("Facility is required", ENTITY_NAME, "facility.required");
        }

        List<FacilityDTO> activeFacilities;
        try {
            activeFacilities = facilityClient.getActiveFacilities();
        } catch (Exception ex) {
            log.error("Failed to load active facilities for complaint submit", ex);
            throw new BadRequestAlertException(
                "Unable to validate facility",
                ENTITY_NAME,
                "facility.validation.failed"
            );
        }

        return activeFacilities
            .stream()
            .filter(facility -> facilityId.equals(facility.id()))
            .findFirst()
            .orElseThrow(() ->
                new NotFoundAlertException("Facility not found: " + facilityId, "facility", "notfound")
            );
    }

    private String generateReference() {
        for (int attempt = 0; attempt < 5; attempt++) {
            Long sequence = patientComplaintRepository.nextReferenceSequence();
            int year = LocalDate.now(ZoneOffset.UTC).getYear();
            String reference = String.format("PC-%d-%06d", year, sequence);
            if (!patientComplaintRepository.existsByComplaintReference(reference)) {
                return reference;
            }
        }
        throw new BadRequestAlertException(
            "Unable to generate a unique complaint reference",
            ENTITY_NAME,
            "reference.generation.failed"
        );
    }

    private Long requireCurrentFacilityId() {
        return SecurityUtils
            .getCurrentUserFacility()
            .orElseThrow(() ->
                new BadRequestAlertException("Current facility is required", ENTITY_NAME, "facility.required")
            );
    }

    private PatientComplaint getAccessibleComplaintWithActions(Long id) {
        Long facilityId = requireCurrentFacilityId();
        PatientComplaint complaint = patientComplaintRepository
            .findWithActionsById(id)
            .orElseThrow(() -> new NotFoundAlertException("Complaint not found: " + id, ENTITY_NAME, "notfound"));
        assertSameFacility(complaint, facilityId);
        // Avoid MultipleBagFetchException: initialize attachments separately from EntityGraph actions.
        complaint.getAttachments().size();
        return complaint;
    }

    private PatientComplaint getAccessibleComplaintForUpdateWithActions(Long id) {
        Long facilityId = requireCurrentFacilityId();
        PatientComplaint complaint = patientComplaintRepository
            .findByIdForUpdate(id)
            .orElseThrow(() -> new NotFoundAlertException("Complaint not found: " + id, ENTITY_NAME, "notfound"));
        assertSameFacility(complaint, facilityId);
        // Initialize actions inside the locked transaction without combining JOIN FETCH + lock.
        complaint.getActions().size();
        return complaint;
    }

    private void assertSameFacility(PatientComplaint complaint, Long facilityId) {
        if (!facilityId.equals(complaint.getFacilityId())) {
            // Do not reveal that the complaint exists in another facility.
            throw new NotFoundAlertException(
                "Complaint not found: " + complaint.getId(),
                ENTITY_NAME,
                "notfound"
            );
        }
    }

    private void assertModifiable(PatientComplaint complaint) {
        if (
            complaint.getStatus() == PatientComplaintStatus.RESOLVED ||
            complaint.getStatus() == PatientComplaintStatus.CLOSED
        ) {
            throw new BadRequestAlertException(
                "Resolved or closed complaints must be reopened before modification",
                ENTITY_NAME,
                "complaint.locked"
            );
        }
    }

    private PatientComplaintAction requireAction(PatientComplaint complaint, Long actionId) {
        return complaint
            .getActions()
            .stream()
            .filter(action -> actionId.equals(action.getId()))
            .findFirst()
            .orElseThrow(() ->
                new NotFoundAlertException("Complaint action not found: " + actionId, ENTITY_NAME, "action.notfound")
            );
    }

    private boolean hasIncompleteActions(PatientComplaint complaint) {
        return complaint.getActions().stream().anyMatch(action -> action.getStatus() != PatientComplaintActionStatus.DONE);
    }

    private UserDTO requireUser(Long userId) {
        try {
            UserDTO user = userClient.getUserByUserId(userId);
            if (user == null || user.id() == null) {
                throw new NotFoundAlertException("User not found: " + userId, "user", "notfound");
            }
            return user;
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException("User not found: " + userId, "user", "notfound");
        }
    }

    /**
     * Assignees must both exist and have a role at the complaint facility
     * ({@code GET /api/setup/user-role/users/by-facility/{facilityId}}).
     */
    private UserDTO requireAssigneeInFacility(Long userId, Long facilityId) {
        UserDTO user = requireUser(userId);
        List<FacilityUserDTO> facilityUsers;
        try {
            facilityUsers = userRoleClient.getUsersByFacility(facilityId);
        } catch (Exception ex) {
            log.error("Failed to validate assignee facility membership userId={} facilityId={}", userId, facilityId, ex);
            throw new BadRequestAlertException(
                "Unable to validate assignee facility access",
                ENTITY_NAME,
                "assignee.facility.validation.failed"
            );
        }

        boolean belongsToFacility = facilityUsers
            .stream()
            .anyMatch(facilityUser -> userId.equals(facilityUser.id()));

        if (!belongsToFacility) {
            throw new BadRequestAlertException(
                "Assignee does not have access to the complaint facility",
                ENTITY_NAME,
                "assignee.facility.access.denied"
            );
        }
        return user;
    }

    private UserDTO requireUserByLogin(String login) {
        try {
            UserDTO user = userClient.getUserByLogin(login);
            if (user == null || user.id() == null) {
                throw new NotFoundAlertException("User not found: " + login, "user", "notfound");
            }
            return user;
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException("User not found: " + login, "user", "notfound");
        }
    }

    private Long resolveCurrentUserIdOrNull() {
        return SecurityUtils
            .getCurrentUserLogin()
            .map(login -> {
                try {
                    return userClient.getUserId(login);
                } catch (Exception ex) {
                    log.warn("Unable to resolve current user id for login={}", login, ex);
                    return null;
                }
            })
            .orElse(null);
    }

    private boolean isOverdue(PatientComplaint complaint, Instant now) {
        if (complaint.getTargetDeadline() == null) {
            return false;
        }
        if (
            complaint.getStatus() == PatientComplaintStatus.RESOLVED ||
            complaint.getStatus() == PatientComplaintStatus.CLOSED
        ) {
            return false;
        }
        return complaint.getTargetDeadline().isBefore(now);
    }

    private String formatUserName(UserDTO user) {
        String fullName = ((user.firstName() == null ? "" : user.firstName().trim()) +
            " " +
            (user.lastName() == null ? "" : user.lastName().trim())).trim();
        if (!fullName.isBlank()) {
            return fullName;
        }
        return user.login();
    }

    private PatientComplaintListVM toListVM(PatientComplaint complaint, Instant overdueBefore) {
        PatientComplaintListVM vm = new PatientComplaintListVM();
        vm.setId(complaint.getId());
        vm.setReference(complaint.getComplaintReference());
        vm.setAnonymous(complaint.isAnonymous());
        vm.setPatientName(displayPatientName(complaint));
        vm.setSubject(complaint.getSubject());
        vm.setFeedbackType(complaint.getFeedbackType());
        vm.setComplaintType(complaint.getComplaintType());
        vm.setIncidentDate(complaint.getIncidentDate());
        vm.setFacilityId(complaint.getFacilityId());
        vm.setFacilityName(complaint.getFacilityName());
        vm.setDepartmentId(complaint.getDepartmentId());
        vm.setDepartmentName(complaint.getDepartmentName());
        vm.setAssignedUserId(complaint.getAssignedUserId());
        vm.setAssignedUserName(complaint.getAssignedUserName());
        vm.setStatus(complaint.getStatus());
        vm.setTargetDeadline(complaint.getTargetDeadline());
        vm.setReceivedDate(complaint.getReceivedDate());
        vm.setOverdue(isOverdue(complaint, overdueBefore));
        return vm;
    }

    private PatientComplaintDetailVM toDetailVM(PatientComplaint complaint, Instant overdueBefore) {
        PatientComplaintDetailVM vm = new PatientComplaintDetailVM();
        vm.setId(complaint.getId());
        vm.setReference(complaint.getComplaintReference());
        vm.setAnonymous(complaint.isAnonymous());
        vm.setPatientName(displayPatientName(complaint));
        vm.setPatientIdentifier(complaint.isAnonymous() ? null : complaint.getPatientIdentifier());
        vm.setDocumentType(complaint.isAnonymous() ? null : complaint.getDocumentType());
        vm.setDocumentNumber(complaint.isAnonymous() ? null : complaint.getDocumentNumber());
        vm.setContactEmail(complaint.getContactEmail());
        vm.setContactPhone(complaint.getContactPhone());
        vm.setPreferredContactMethod(complaint.getPreferredContactMethod());
        vm.setOnBehalf(complaint.getOnBehalf());
        vm.setRelationshipToPatient(complaint.getRelationshipToPatient());
        vm.setConsentToContact(complaint.isConsentToContact());
        vm.setFacilityId(complaint.getFacilityId());
        vm.setFacilityName(complaint.getFacilityName());
        vm.setDepartmentId(complaint.getDepartmentId());
        vm.setDepartmentName(complaint.getDepartmentName());
        vm.setFeedbackType(complaint.getFeedbackType());
        vm.setComplaintType(complaint.getComplaintType());
        vm.setIncidentDate(complaint.getIncidentDate());
        vm.setIncidentTime(complaint.getIncidentTime());
        vm.setSubject(complaint.getSubject());
        vm.setDescription(complaint.getDescription());
        vm.setInvolvedStaffNames(complaint.getInvolvedStaffNames());
        vm.setDesiredResolution(complaint.getDesiredResolution());
        vm.setAssignedUserId(complaint.getAssignedUserId());
        vm.setAssignedUserName(complaint.getAssignedUserName());
        vm.setStatus(complaint.getStatus());
        vm.setTargetDeadline(complaint.getTargetDeadline());
        vm.setReceivedDate(complaint.getReceivedDate());
        vm.setResolutionSummary(complaint.getResolutionSummary());
        vm.setReopenReason(complaint.getReopenReason());
        vm.setOverdue(isOverdue(complaint, overdueBefore));
        vm.setActions(complaint.getActions().stream().map(this::toActionVM).toList());
        vm.setAttachments(
            complaint
                .getAttachments()
                .stream()
                .filter(attachment -> attachment.getDeletedAt() == null)
                .map(this::toAttachmentVM)
                .toList()
        );
        return vm;
    }

    private PatientComplaintActionVM toActionVM(PatientComplaintAction action) {
        PatientComplaintActionVM vm = new PatientComplaintActionVM();
        vm.setId(action.getId());
        vm.setDescription(action.getDescription());
        vm.setAssignedUserId(action.getAssignedUserId());
        vm.setAssignedUserName(action.getAssignedUserName());
        vm.setDeadline(action.getDeadline());
        vm.setStatus(action.getStatus());
        vm.setResult(action.getResult());
        vm.setCompletedByUserId(action.getCompletedByUserId());
        vm.setCompletedBy(action.getCompletedBy());
        vm.setCompletedAt(action.getCompletedAt());
        return vm;
    }

    private PatientComplaintAttachmentVM toAttachmentVM(PatientComplaintAttachment attachment) {
        PatientComplaintAttachmentVM vm = new PatientComplaintAttachmentVM();
        vm.setId(attachment.getId());
        vm.setFilename(attachment.getFilename());
        vm.setMimeType(attachment.getMimeType());
        vm.setSizeBytes(attachment.getSizeBytes());
        vm.setCreatedDate(attachment.getCreatedDate());
        return vm;
    }

    private String displayPatientName(PatientComplaint complaint) {
        if (complaint.isAnonymous()) {
            return "Anonymous";
        }
        return complaint.getPatientName();
    }

    private void normalizeSubmitDto(PatientComplaintSubmitDTO dto) {
        dto.setPatientName(blankToNull(dto.getPatientName()));
        dto.setPatientIdentifier(blankToNull(dto.getPatientIdentifier()));
        dto.setDocumentNumber(blankToNull(dto.getDocumentNumber()));
        if (dto.getDocumentNumber() == null) {
            dto.setDocumentType(null);
        }
        dto.setContactEmail(blankToNull(dto.getContactEmail()));
        dto.setContactPhone(blankToNull(dto.getContactPhone()));
        dto.setRelationshipToPatient(blankToNull(dto.getRelationshipToPatient()));
        dto.setInvolvedStaffNames(blankToNull(dto.getInvolvedStaffNames()));
        dto.setDesiredResolution(blankToNull(dto.getDesiredResolution()));
    }

    private void validateConsent(PatientComplaintSubmitDTO dto) {
        if (!Boolean.TRUE.equals(dto.getConsentToContact())) {
            throw new BadRequestAlertException(
                "Consent to be contacted and to review the medical record is required",
                ENTITY_NAME,
                "consent.required"
            );
        }
    }

    private void validateSubmitIdentity(PatientComplaintSubmitDTO dto) {
        if (dto.getAnonymous() == null) {
            throw new BadRequestAlertException("Complaint mode is required", ENTITY_NAME, "anonymous.required");
        }
        if (Boolean.TRUE.equals(dto.getAnonymous())) {
            if (
                dto.getPatientName() != null ||
                dto.getPatientIdentifier() != null ||
                dto.getDocumentType() != null ||
                dto.getDocumentNumber() != null ||
                dto.getContactEmail() != null ||
                dto.getContactPhone() != null ||
                dto.getPreferredContactMethod() != null ||
                dto.getOnBehalf() != null ||
                dto.getRelationshipToPatient() != null
            ) {
                throw new BadRequestAlertException(
                    "Anonymous complaints must not include identifying or contact fields",
                    ENTITY_NAME,
                    "anonymous.identifying.fields"
                );
            }
            return;
        }
        if (dto.getDocumentNumber() != null && dto.getDocumentType() == null) {
            throw new BadRequestAlertException(
                "Document type is required when document number is provided",
                ENTITY_NAME,
                "documentType.required"
            );
        }
        if (dto.getDocumentType() != null && dto.getDocumentNumber() == null) {
            throw new BadRequestAlertException(
                "Document number is required when document type is provided",
                ENTITY_NAME,
                "documentNumber.required"
            );
        }
        if (dto.getPatientName() == null || dto.getPatientName().isBlank()) {
            throw new BadRequestAlertException("Full name is required", ENTITY_NAME, "patientName.required");
        }
        if (dto.getContactPhone() == null || dto.getContactPhone().isBlank()) {
            throw new BadRequestAlertException("Contact number is required", ENTITY_NAME, "contactPhone.required");
        }
        if (dto.getPreferredContactMethod() == null) {
            throw new BadRequestAlertException(
                "Preferred contact method is required",
                ENTITY_NAME,
                "preferredContactMethod.required"
            );
        }
        if (
            dto.getPreferredContactMethod() == PatientComplaintPreferredContactMethod.EMAIL &&
            (dto.getContactEmail() == null || dto.getContactEmail().isBlank())
        ) {
            throw new BadRequestAlertException(
                "Email is required when preferred contact method is Email",
                ENTITY_NAME,
                "contactEmail.required.for.preferred"
            );
        }
        if (dto.getOnBehalf() == null) {
            throw new BadRequestAlertException(
                "Please indicate whether you are submitting on behalf of someone else",
                ENTITY_NAME,
                "onBehalf.required"
            );
        }
        if (!Boolean.TRUE.equals(dto.getOnBehalf())) {
            dto.setRelationshipToPatient(null);
        }
    }

    private void validateVisitFields(PatientComplaintSubmitDTO dto) {
        if (dto.getFeedbackType() == null) {
            throw new BadRequestAlertException("Type of feedback is required", ENTITY_NAME, "feedbackType.required");
        }
        if (dto.getDepartmentId() == null) {
            throw new BadRequestAlertException(
                "Department or clinic is required",
                ENTITY_NAME,
                "department.required"
            );
        }
        if (dto.getIncidentDate() == null) {
            throw new BadRequestAlertException(
                "Date of visit is required",
                ENTITY_NAME,
                "incidentDate.required"
            );
        }
        if (dto.getIncidentTime() == null) {
            throw new BadRequestAlertException(
                "Time of visit is required",
                ENTITY_NAME,
                "incidentTime.required"
            );
        }
        LocalDate todayBusiness = LocalDate.now(BUSINESS_ZONE);
        if (dto.getIncidentDate().isAfter(todayBusiness)) {
            throw new BadRequestAlertException(
                "Visit date cannot be in the future",
                ENTITY_NAME,
                "incidentDate.future"
            );
        }
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new BadRequestAlertException("Details are required", ENTITY_NAME, "description.required");
        }
    }

    private static String formatPatientDisplayName(Patient patient) {
        String joined = String
            .join(
                " ",
                blankToEmpty(patient.getFirstName()),
                blankToEmpty(patient.getSecondName()),
                blankToEmpty(patient.getThirdName()),
                blankToEmpty(patient.getLastName())
            )
            .trim()
            .replaceAll(" +", " ");
        return joined.isEmpty() ? null : joined;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String requireActiveDepartmentForFacility(Long departmentId, Long facilityId) {
        if (departmentId == null) {
            throw new BadRequestAlertException(
                "Department or clinic is required",
                ENTITY_NAME,
                "department.required"
            );
        }
        DepartmentDTO department = departmentHelper.getDepartmentInternal(departmentId);
        if (!facilityId.equals(department.facilityId())) {
            throw new BadRequestAlertException(
                "Department does not belong to the selected facility",
                ENTITY_NAME,
                "department.facility.mismatch"
            );
        }
        if (!Boolean.TRUE.equals(department.isActive())) {
            throw new BadRequestAlertException(
                "Department is not active",
                ENTITY_NAME,
                "department.inactive"
            );
        }
        return department.name();
    }

    private void validateOptionalPhone(String phone) {
        if (phone == null) {
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BadRequestAlertException(
                "Phone number format is invalid",
                ENTITY_NAME,
                "contactPhone.invalid"
            );
        }
    }

    private static String subjectFromFeedbackType(PatientComplaintFeedbackType feedbackType) {
        if (feedbackType == null) {
            return "Patient Feedback";
        }
        return switch (feedbackType) {
            case COMPLIMENT -> "Compliment";
            case SUGGESTION -> "Suggestion";
            case CONCERN_OR_COMPLAINT -> "Concern or Complaint";
        };
    }

    private void storeAttachments(PatientComplaint complaint, List<MultipartFile> files, Instant now) {
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            long size = file.getSize();
            validateAttachmentFile(mime, size);

            String originalName = sanitizeFilename(
                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
                    ? "file"
                    : file.getOriginalFilename()
            );
            String key =
                "complaints/" +
                complaint.getId() +
                "/" +
                YYYY.format(now) +
                "/" +
                MM.format(now) +
                "/" +
                UUID.randomUUID() +
                "_" +
                originalName;

            try {
                attachmentStorageService.put(key, mime, size, file.getInputStream());
            } catch (Exception ex) {
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Complaint attachment upload failed",
                    ex
                );
            }

            PatientComplaintAttachment attachment = new PatientComplaintAttachment();
            attachment.setSpaceKey(key);
            attachment.setFilename(originalName);
            attachment.setMimeType(mime);
            attachment.setSizeBytes(size);
            complaint.addAttachment(attachment);
        }
    }

    private void validateAttachmentFile(String mime, long size) {
        if (
            attachmentProperties.getAllowed() != null &&
            !attachmentProperties.getAllowed().isEmpty() &&
            !attachmentProperties.getAllowed().contains(mime)
        ) {
            throw new BadRequestAlertException(
                "Unsupported file type",
                ATTACHMENT_ENTITY,
                "unsupported_type"
            );
        }
        if (attachmentProperties.getMaxBytes() > 0 && size > attachmentProperties.getMaxBytes()) {
            throw new BadRequestAlertException("File too large", ATTACHMENT_ENTITY, "too_large");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/\\r\\n\\t]", "_").trim();
    }
}
