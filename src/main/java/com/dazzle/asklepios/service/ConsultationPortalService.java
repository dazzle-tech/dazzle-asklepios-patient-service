package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.enumeration.ConsultationLevel;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.consultation.ConsultationRejectDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationResponseDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitErrorDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitRequestDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitResultDTO;
import com.dazzle.asklepios.service.helper.DepartmentHelper;
import com.dazzle.asklepios.service.helper.NotificationHelper;
import com.dazzle.asklepios.service.helper.PractitionerHelper;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class ConsultationPortalService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsultationPortalService.class);

    private final ConsultationRepository consultationRepository;
    private final DepartmentHelper departmentHelper;
    private final NotificationHelper notificationHelper;
    private final PractitionerHelper practitionerHelper;

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        if (username == null) {
            LOG.warn("[ConsultationPortalService] AUTH - unauthenticated request");
            throw new BadRequestAlertException(
                    "unauthenticated",
                    "consultation",
                    "No authenticated user"
            );
        }
        return username;
    }

    @Transactional(readOnly = true)
    public Page<Consultation> listPractitionerAndDepartmentConsultations(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            Long toDepartmentId,
            List<Long> fromDepartmentIds,
            Pageable pageable,
            boolean showRejected
    ) {
        LOG.debug(
                "[SEARCH_MERGED] start fromDate={} toDate={} fromFacilityId={} practitionerId={} toDepartmentId={} fromDepartmentIds={} showRejected={}",
                fromDate, toDate, fromFacilityId, practitionerId, toDepartmentId, fromDepartmentIds, showRejected
        );

        boolean hasFromDepartments = hasFromDepartmentsFilter(fromDepartmentIds);
        LOG.debug("[SEARCH_MERGED] hasFromDepartments={}", hasFromDepartments);

        List<ConsultationStatus> excludedStatuses = resolveExcludedStatusesForMergedSearch(showRejected);
        LOG.debug("[SEARCH_MERGED] excludedStatuses={}", excludedStatuses);

        List<Consultation> merged = new ArrayList<>();

        if (practitionerId != null) {
            Page<Consultation> practitionerPage = fetchPractitionerConsultations(
                    fromDate,
                    toDate,
                    fromFacilityId,
                    practitionerId,
                    fromDepartmentIds,
                    excludedStatuses,
                    Pageable.unpaged(),
                    hasFromDepartments
            );

            LOG.debug("[SEARCH_MERGED] fetched practitionerCount={}", practitionerPage.getContent().size());
            merged.addAll(practitionerPage.getContent());
        } else {
            LOG.debug("[SEARCH_MERGED] practitionerId is null, skipping practitioner consultations query");
        }

        if (toDepartmentId != null) {
            Page<Consultation> departmentPage = fetchDepartmentConsultations(
                    fromDate,
                    toDate,
                    fromFacilityId,
                    toDepartmentId,
                    fromDepartmentIds,
                    excludedStatuses,
                    Pageable.unpaged(),
                    hasFromDepartments
            );

            LOG.debug("[SEARCH_MERGED] fetched departmentCount={}", departmentPage.getContent().size());
            merged.addAll(departmentPage.getContent());
        } else {
            LOG.debug("[SEARCH_MERGED] toDepartmentId is null, skipping department consultations query");
        }

        List<Consultation> mergedDistinctSorted = mergeDeduplicateAndSortConsultations(merged);
        LOG.debug("[SEARCH_MERGED] mergedDistinctCount={}", mergedDistinctSorted.size());

        Page<Consultation> result = paginateConsultations(mergedDistinctSorted, pageable);
        LOG.debug(
                "[SEARCH_MERGED] end pageNumber={} pageSize={} returnedElements={} totalElements={}",
                result.getNumber(),
                result.getSize(),
                result.getNumberOfElements(),
                result.getTotalElements()
        );

        return result;
    }

    private boolean hasFromDepartmentsFilter(List<Long> fromDepartmentIds) {
        boolean hasFilter = fromDepartmentIds != null && !fromDepartmentIds.isEmpty();
        LOG.debug("[SEARCH_MERGED] hasFromDepartmentsFilter={} fromDepartmentIds={}", hasFilter, fromDepartmentIds);
        return hasFilter;
    }

    private List<ConsultationStatus> resolveExcludedStatusesForMergedSearch(boolean showRejected) {
        List<ConsultationStatus> excludedStatuses =
                showRejected
                        ? List.of(ConsultationStatus.CANCELLED)
                        : List.of(ConsultationStatus.CANCELLED, ConsultationStatus.REJECTED);
        LOG.debug("[SEARCH_MERGED] resolved excludedStatuses={} showRejected={}", excludedStatuses, showRejected);
        return excludedStatuses;
    }

    private Page<Consultation> fetchPractitionerConsultations(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long practitionerId,
            List<Long> fromDepartmentIds, List<ConsultationStatus> excludedStatuses,
            Pageable pageable, boolean hasDepartments
    ) {
        if (practitionerId == null) {
            LOG.debug("[SEARCH_MERGED] practitionerId is null -> returning empty practitioner page");
            return Page.empty(pageable);
        }

        LOG.debug(
                "[SEARCH_MERGED] fetching practitioner consultations practitionerId={} hasDepartments={} excludedStatuses={} fromFacilityId={} fromDate={} toDate={}",
                practitionerId, hasDepartments, excludedStatuses, fromFacilityId, fromDate, toDate
        );

        Page<Consultation> page = hasDepartments
                ? fetchPractitionerConsultationsWithFromDepartments(
                fromDate, toDate, fromFacilityId, practitionerId, fromDepartmentIds, excludedStatuses, pageable)
                : fetchPractitionerConsultationsWithoutFromDepartments(
                fromDate, toDate, fromFacilityId, practitionerId, excludedStatuses, pageable);

        LOG.debug("[SEARCH_MERGED] practitioner consultations fetched count={}", page.getContent().size());
        return page;
    }

    private Page<Consultation> fetchPractitionerConsultationsWithFromDepartments(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long practitionerId,
            List<Long> fromDepartmentIds, List<ConsultationStatus> excludedStatuses, Pageable pageable
    ) {
        LOG.debug(
                "[SEARCH_MERGED] practitioner query WITH fromDepartmentIds practitionerId={} fromDepartmentIds={}",
                practitionerId, fromDepartmentIds
        );
        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndFromDepartmentIdInAndStatusNotIn(
                        fromDate, toDate, fromFacilityId, practitionerId, fromDepartmentIds, excludedStatuses, pageable);
    }

    private Page<Consultation> fetchPractitionerConsultationsWithoutFromDepartments(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long practitionerId,
            List<ConsultationStatus> excludedStatuses, Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] practitioner query WITHOUT fromDepartmentIds practitionerId={}", practitionerId);
        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndStatusNotIn(
                        fromDate, toDate, fromFacilityId, practitionerId, excludedStatuses, pageable);
    }

    private Page<Consultation> fetchDepartmentConsultations(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long toDepartmentId,
            List<Long> fromDepartmentIds, List<ConsultationStatus> excludedStatuses,
            Pageable pageable, boolean hasDepartments
    ) {
        if (toDepartmentId == null) {
            LOG.debug("[SEARCH_MERGED] toDepartmentId is null -> returning empty department page");
            return Page.empty(pageable);
        }

        LOG.debug(
                "[SEARCH_MERGED] fetching department consultations toDepartmentId={} hasDepartments={} excludedStatuses={} fromFacilityId={} fromDate={} toDate={}",
                toDepartmentId, hasDepartments, excludedStatuses, fromFacilityId, fromDate, toDate
        );

        Page<Consultation> page = hasDepartments
                ? fetchDepartmentConsultationsWithFromDepartments(
                fromDate, toDate, fromFacilityId, toDepartmentId, fromDepartmentIds, excludedStatuses, pageable)
                : fetchDepartmentConsultationsWithoutFromDepartments(
                fromDate, toDate, fromFacilityId, toDepartmentId, excludedStatuses, pageable);

        LOG.debug("[SEARCH_MERGED] department consultations fetched count={}", page.getContent().size());
        return page;
    }

    private Page<Consultation> fetchDepartmentConsultationsWithFromDepartments(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long toDepartmentId,
            List<Long> fromDepartmentIds, List<ConsultationStatus> excludedStatuses, Pageable pageable
    ) {
        LOG.debug(
                "[SEARCH_MERGED] department query WITH fromDepartmentIds toDepartmentId={} fromDepartmentIds={}",
                toDepartmentId, fromDepartmentIds
        );
        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndFromDepartmentIdInAndStatusNotIn(
                        fromDate, toDate, fromFacilityId, toDepartmentId, fromDepartmentIds, excludedStatuses, pageable);
    }

    private Page<Consultation> fetchDepartmentConsultationsWithoutFromDepartments(
            Instant fromDate, Instant toDate, Long fromFacilityId, Long toDepartmentId,
            List<ConsultationStatus> excludedStatuses, Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] department query WITHOUT fromDepartmentIds toDepartmentId={}", toDepartmentId);
        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndStatusNotIn(
                        fromDate, toDate, fromFacilityId, toDepartmentId, excludedStatuses, pageable);
    }

    private List<Consultation> mergeDeduplicateAndSortConsultations(List<Consultation> consultations) {
        LOG.debug("[SEARCH_MERGED] merging raw results count={}", consultations.size());

        Map<Long, Consultation> distinctById = new LinkedHashMap<>();

        for (Consultation consultation : consultations) {
            if (consultation == null) {
                continue;
            }

            Long id = consultation.getId();
            if (id == null) {
                LOG.warn("[SEARCH_MERGED] consultation without id found, skipping");
                continue;
            }

            if (distinctById.containsKey(id)) {
                LOG.debug("[SEARCH_MERGED] duplicate consultation found id={}, keeping first occurrence", id);
                continue;
            }

            distinctById.put(id, consultation);
        }

        List<Consultation> result = distinctById.values()
                .stream()
                .sorted(
                        Comparator
                                .comparing((Consultation c) ->
                                        c.getConsultationLevel() == ConsultationLevel.CRITICAL ? 0 : 1)
                                .thenComparing(
                                        Consultation::getCreatedDate,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                                .thenComparing(
                                        Consultation::getId,
                                        Comparator.nullsLast(Long::compareTo)
                                )
                )
                .peek(c -> LOG.trace(
                        "[SEARCH_MERGED] sorted consultation id={} level={} createdDate={}",
                        c.getId(), c.getConsultationLevel(), c.getCreatedDate()))
                .toList();

        LOG.debug("[SEARCH_MERGED] deduplicated result count={}", result.size());
        return result;
    }

    private Page<Consultation> paginateConsultations(List<Consultation> consultations, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(consultations);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), consultations.size());

        if (start >= consultations.size()) {
            return new PageImpl<>(List.of(), pageable, consultations.size());
        }

        List<Consultation> pageContent = consultations.subList(start, end);
        return new PageImpl<>(pageContent, pageable, consultations.size());
    }

    @Transactional
    public Consultation confirmConsultation(Long consultationId) {

        String username = currentUsername();
        LOG.info("[CONFIRM] Consultation id={} confirmedBy={}", consultationId, username);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Consultation not found", "consultation", "notfound"));

        consultation.setStatus(ConsultationStatus.CONFIRMED);
        consultation.setConfirmedDate(Instant.now());
        consultation.setConfirmedBy(username);

        Consultation saved = consultationRepository.saveAndFlush(consultation);
        LOG.info("[CONFIRM] Consultation confirmed successfully id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Consultation rejectConsultation(Long consultationId, ConsultationRejectDTO dto) {
        String username = currentUsername();
        LOG.info("[REJECT] Consultation id={} rejectedBy={} reason={}", consultationId, username, dto.reason());

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Consultation not found", "consultation", "notfound"));

        consultation.setStatus(ConsultationStatus.REJECTED);
        consultation.setRejectedDate(Instant.now());
        consultation.setRejectedBy(username);
        consultation.setRejectReason(dto.reason());

        Consultation saved = consultationRepository.saveAndFlush(consultation);

        notificationForRequestingDepartmentWhenConsultationEvent(
                saved,
                NotificationCode.CONSULTATION_REJECTED,
                Map.of(
                        "rejectReason", dto.reason() != null ? dto.reason() : ""
                )
        );

        LOG.info("[REJECT] Consultation rejected successfully id={}", saved.getId());
        return saved;
    }

    @Transactional
    public Consultation submitConsultationResponse(Long consultationId, ConsultationResponseDTO dto) {
        String username = currentUsername();
        LOG.info("[ADD_RESPONSE] Consultation id={} responseBy={}", consultationId, username);

        Consultation consultation = consultationRepository.findById(consultationId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Consultation not found", "consultation", "notfound"));

        consultation.setResponseText(dto.responseText());
        consultation.setResponseBy(username);
        consultation.setResponseDate(Instant.now());
        consultation.setStatus(ConsultationStatus.READY);

        Consultation saved = consultationRepository.saveAndFlush(consultation);
        LOG.info("[ADD_RESPONSE] Response added successfully id={}", saved.getId());
        return saved;
    }

    @Transactional
    public ConsultationSubmitResultDTO submitConsultations(ConsultationSubmitRequestDTO consultationSubmitRequestDTO) {
        LOG.info("[SUBMIT] consultationIds={}", consultationSubmitRequestDTO.consultationIds());
        String username = currentUsername();

        List<Long> consultationIds = consultationSubmitRequestDTO.consultationIds();
        List<Consultation> consultations = consultationRepository.findAllById(consultationIds);

        List<ConsultationSubmitErrorDTO> errors = consultations.stream()
                .filter(c -> c.getResponseText() == null
                        || c.getResponseText().isBlank()
                        || c.getStatus() != ConsultationStatus.READY)
                .map(c -> new ConsultationSubmitErrorDTO(
                        c.getId(),
                        c.getConsultationNumber(),
                        (c.getResponseText() == null || c.getResponseText().isBlank())
                                ? "Consultation has no response"
                                : "Consultation cannot be submitted with status " + c.getStatus()
                ))
                .toList();

        List<Consultation> consultationsToSubmit = consultations.stream()
                .filter(c -> c.getResponseText() != null
                        && !c.getResponseText().isBlank()
                        && c.getStatus() == ConsultationStatus.READY)
                .toList();

        consultationsToSubmit.forEach(c -> {
            c.setStatus(ConsultationStatus.SUBMITTED);
            c.setSubmittedBy(username);
            c.setSubmittedDate(Instant.now());
        });
        List<Consultation> savedConsultations = consultationRepository.saveAll(consultationsToSubmit);

        savedConsultations.forEach(consultation ->
                notificationForRequestingDepartmentWhenConsultationEvent(
                        consultation,
                        NotificationCode.CONSULTATION_SUBMITTED,
                        Map.of()
                )
        );
        LOG.info("[SUBMIT] submittedCount={} errorsCount={}", consultationsToSubmit.size(), errors.size());

        return new ConsultationSubmitResultDTO(consultationsToSubmit.size(), errors);
    }

    private void notificationForRequestingDepartmentWhenConsultationEvent(Consultation consultation, NotificationCode notificationCode, Map<String, Object> extraData) {
        try {
            if (consultation == null || notificationCode == null) {
                return;
            }

            Long departmentId = consultation.getFromDepartmentId();
            DepartmentDTO fromDepartment = departmentHelper.getDepartment(departmentId);
            DepartmentDTO toDepartment = departmentHelper.getDepartment(consultation.getToDepartmentId());
            if (departmentId == null) {
                LOG.warn(
                        "Skip consultation notification because requesting department is missing. consultationId={}, code={}",
                        consultation.getId(),
                        notificationCode
                );
                return;
            }
            String login = SecurityUtils.getCurrentUserLogin().orElse(null);
            PractitionerDTO practitioner = null;
            if (consultation.getPractitionerId() != null) {
                practitioner = practitionerHelper.getPractitioner(consultation.getPractitionerId());
            }
            Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = notificationHelper.resolveRecipients(departmentId, login, consultation.getCreatedBy(), consultation.getPatient(), practitioner);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("consultationId", consultation.getId());
            data.put("consultationNumber", consultation.getConsultationNumber());
            data.put("patientId", consultation.getPatient() != null ? consultation.getPatient().getId() : "");
            data.put("patientName", consultation.getPatient() != null ? notificationHelper.getPatientName(consultation.getPatient()) : "");
            data.put("encounterId", consultation.getEncounter() != null ? consultation.getEncounter().getId() : "");
            data.put("fromFacilityId", consultation.getFromFacilityId());
            data.put("toFacilityId", consultation.getToFacilityId());
            data.put("fromDepartmentId", consultation.getFromDepartmentId());
            data.put("fromDepartmentName", fromDepartment.name());
            data.put("toDepartmentId", consultation.getToDepartmentId());
            data.put("toDepartmentName", toDepartment.name());
            data.put("consultationType", consultation.getConsultationType() != null ? consultation.getConsultationType().toString() : "");
            data.put("destinationType", consultation.getDestinationType() != null ? consultation.getDestinationType().toString() : "");
            data.put("consultationLevel", consultation.getConsultationLevel() != null ? consultation.getConsultationLevel().toString() : "");
            data.put("consultationMethod", consultation.getConsultationMethod() != null ? consultation.getConsultationMethod().toString() : "");
            data.put("status", consultation.getStatus() != null ? consultation.getStatus().toString() : "");
            data.put("submittedBy", consultation.getSubmittedBy() != null ? consultation.getSubmittedBy() : "");
            data.put("submittedDate", consultation.getSubmittedDate() != null ? consultation.getSubmittedDate().toString() : "");
            data.put("rejectedBy", consultation.getRejectedBy() != null ? consultation.getRejectedBy() : "");
            data.put("rejectedDate", consultation.getRejectedDate() != null ? consultation.getRejectedDate().toString() : "");
            data.put("rejectReason", consultation.getRejectReason() != null ? consultation.getRejectReason() : "");

            if (extraData != null && !extraData.isEmpty()) {
                data.putAll(extraData);
            }


            LOG.debug(
                    "Creating consultation in-app notification. consultationId={}, code={}, departmentId={}, recipientsByRule={}",
                    consultation.getId(),
                    notificationCode,
                    departmentId,
                    recipientsByRule
            );

            notificationHelper.sendNotification(null,
                    notificationCode,
                    "en",
                    recipientsByRule,
                    data,
                    "CONSULTATION",
                    consultation.getId());
        } catch (Exception e) {
            LOG.warn(
                    "Failed to create consultation notification. consultationId={}, code={}, error={}",
                    consultation.getId(),
                    notificationCode,
                    e.getMessage()
            );
        }
    }
}