package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.Consultation;
import com.dazzle.asklepios.domain.enumeration.ConsultationLevel;
import com.dazzle.asklepios.domain.enumeration.ConsultationStatus;
import com.dazzle.asklepios.repository.ConsultationRepository;
import com.dazzle.asklepios.service.dto.consultation.ConsultationConfirmDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationRejectDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationResponseDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitErrorDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitRequestDTO;
import com.dazzle.asklepios.service.dto.consultation.ConsultationSubmitResultDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional
public class ConsultationPortalService {

    private static final Logger LOG =
            LoggerFactory.getLogger(ConsultationPortalService.class);

    private final ConsultationRepository consultationRepository;

    public ConsultationPortalService(
            ConsultationRepository consultationRepository
    ) {
        this.consultationRepository = consultationRepository;
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

        Page<Consultation> practitionerPage =
                fetchPractitionerConsultations(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        practitionerId,
                        fromDepartmentIds,
                        excludedStatuses,
                        pageable,
                        hasFromDepartments
                );

        Page<Consultation> departmentPage =
                fetchDepartmentConsultations(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        toDepartmentId,
                        fromDepartmentIds,
                        excludedStatuses,
                        pageable,
                        hasFromDepartments
                );

        LOG.debug("[SEARCH_MERGED] fetched practitionerCount={} departmentCount={}",
                practitionerPage.getContent().size(),
                departmentPage.getContent().size()
        );

        List<Consultation> mergedDistinct =
                mergeAndSortConsultations(practitionerPage, departmentPage);

        LOG.debug("[SEARCH_MERGED] mergedDistinctCount={}", mergedDistinct.size());

        Page<Consultation> result = new PageImpl<>(
                mergedDistinct,
                pageable,
                mergedDistinct.size()
        );

        LOG.debug("[SEARCH_MERGED] end pageSize={} totalElements={}",
                result.getSize(),
                result.getTotalElements()
        );

        return result;
    }

    private boolean hasFromDepartmentsFilter(List<Long> fromDepartmentIds) {
        boolean hasFilter = fromDepartmentIds != null && !fromDepartmentIds.isEmpty();

        LOG.debug("[SEARCH_MERGED] hasFromDepartmentsFilter={} fromDepartmentIds={}",
                hasFilter, fromDepartmentIds);

        return hasFilter;
    }

    private List<ConsultationStatus> resolveExcludedStatusesForMergedSearch(boolean showRejected) {
        List<ConsultationStatus> excludedStatuses =
                showRejected
                        ? List.of(ConsultationStatus.CANCELLED)
                        : List.of(ConsultationStatus.CANCELLED, ConsultationStatus.REJECTED);

        LOG.debug("[SEARCH_MERGED] resolved excludedStatuses={} showRejected={}",
                excludedStatuses, showRejected);

        return excludedStatuses;
    }

    private Page<Consultation> fetchPractitionerConsultations(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable,
            boolean hasDepartments
    ) {
        LOG.debug("[SEARCH_MERGED] fetching practitioner consultations practitionerId={} hasDepartments={} excludedStatuses={} fromFacilityId={} fromDate={} toDate={}",
                practitionerId, hasDepartments, excludedStatuses, fromFacilityId, fromDate, toDate);

        Page<Consultation> page = hasDepartments
                ? fetchPractitionerConsultationsWithFromDepartments(
                fromDate, toDate, fromFacilityId, practitionerId, fromDepartmentIds, excludedStatuses, pageable
        )
                : fetchPractitionerConsultationsWithoutFromDepartments(
                fromDate, toDate, fromFacilityId, practitionerId, excludedStatuses, pageable
        );

        LOG.debug("[SEARCH_MERGED] practitioner consultations fetched count={}", page.getContent().size());
        return page;
    }

    private Page<Consultation> fetchPractitionerConsultationsWithFromDepartments(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] practitioner query WITH fromDepartmentIds practitionerId={} fromDepartmentIds={}",
                practitionerId, fromDepartmentIds);

        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndFromDepartmentIdInAndStatusNotIn(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        practitionerId,
                        fromDepartmentIds,
                        excludedStatuses,
                        pageable
                );
    }

    private Page<Consultation> fetchPractitionerConsultationsWithoutFromDepartments(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long practitionerId,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] practitioner query WITHOUT fromDepartmentIds practitionerId={}",
                practitionerId);

        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndPractitionerIdAndStatusNotIn(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        practitionerId,
                        excludedStatuses,
                        pageable
                );
    }

    private Page<Consultation> fetchDepartmentConsultations(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long toDepartmentId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable,
            boolean hasDepartments
    ) {
        LOG.debug("[SEARCH_MERGED] fetching department consultations toDepartmentId={} hasDepartments={} excludedStatuses={} fromFacilityId={} fromDate={} toDate={}",
                toDepartmentId, hasDepartments, excludedStatuses, fromFacilityId, fromDate, toDate);

        Page<Consultation> page = hasDepartments
                ? fetchDepartmentConsultationsWithFromDepartments(
                fromDate, toDate, fromFacilityId, toDepartmentId, fromDepartmentIds, excludedStatuses, pageable
        )
                : fetchDepartmentConsultationsWithoutFromDepartments(
                fromDate, toDate, fromFacilityId, toDepartmentId, excludedStatuses, pageable
        );

        LOG.debug("[SEARCH_MERGED] department consultations fetched count={}", page.getContent().size());
        return page;
    }

    private Page<Consultation> fetchDepartmentConsultationsWithFromDepartments(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long toDepartmentId,
            List<Long> fromDepartmentIds,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] department query WITH fromDepartmentIds toDepartmentId={} fromDepartmentIds={}",
                toDepartmentId, fromDepartmentIds);

        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndFromDepartmentIdInAndStatusNotIn(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        toDepartmentId,
                        fromDepartmentIds,
                        excludedStatuses,
                        pageable
                );
    }

    private Page<Consultation> fetchDepartmentConsultationsWithoutFromDepartments(
            Instant fromDate,
            Instant toDate,
            Long fromFacilityId,
            Long toDepartmentId,
            List<ConsultationStatus> excludedStatuses,
            Pageable pageable
    ) {
        LOG.debug("[SEARCH_MERGED] department query WITHOUT fromDepartmentIds toDepartmentId={}",
                toDepartmentId);

        return consultationRepository
                .findByCreatedDateBetweenAndFromFacilityIdAndToDepartmentIdAndStatusNotIn(
                        fromDate,
                        toDate,
                        fromFacilityId,
                        toDepartmentId,
                        excludedStatuses,
                        pageable
                );
    }

    private List<Consultation> mergeAndSortConsultations(
            Page<Consultation> practitionerPage,
            Page<Consultation> departmentPage
    ) {
        LOG.debug(
                "[SEARCH_MERGED] merging results practitionerCount={} departmentCount={}",
                practitionerPage.getContent().size(),
                departmentPage.getContent().size()
        );

        return Stream.concat(
                        practitionerPage.getContent().stream(),
                        departmentPage.getContent().stream()
                )
                .sorted(
                        Comparator
                                .comparing(
                                        (Consultation consultation) ->
                                                consultation.getConsultationLevel() == ConsultationLevel.CRITICAL ? 0 : 1
                                )
                                .thenComparing(
                                        Consultation::getCreatedDate,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                )
                .peek(consultation ->
                        LOG.trace(
                                "[SEARCH_MERGED] sorted consultation id={} level={} createdDate={}",
                                consultation.getId(),
                                consultation.getConsultationLevel(),
                                consultation.getCreatedDate()
                        )
                )
                .toList();
    }

    @Transactional
    public Consultation confirmConsultation(
            Long consultationId,
            ConsultationConfirmDTO dto
    ) {

        LOG.info(
                "[CONFIRM] Consultation id={} confirmedBy={}",
                consultationId,
                dto.confirmedBy()
        );

        Consultation consultation =
                consultationRepository.findById(consultationId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Consultation not found",
                                        "consultation",
                                        "notfound"
                                )
                        );


        consultation.setStatus(ConsultationStatus.CONFIRMED);
        consultation.setConfirmedDate(Instant.now());
        consultation.setConfirmedBy(dto.confirmedBy());

        Consultation saved = consultationRepository.saveAndFlush(consultation);

        LOG.info("[CONFIRM] Consultation confirmed successfully id={}", saved.getId());

        return saved;
    }


    @Transactional
    public Consultation rejectConsultation(
            Long consultationId,
            ConsultationRejectDTO dto
    ) {

        LOG.info(
                "[REJECT] Consultation id={} rejectedBy={} reason={}",
                consultationId,
                dto.rejectedBy(),
                dto.reason()
        );

        Consultation consultation =
                consultationRepository.findById(consultationId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Consultation not found",
                                        "consultation",
                                        "notfound"
                                )
                        );
        consultation.setStatus(ConsultationStatus.REJECTED);
        consultation.setRejectedDate(Instant.now());
        consultation.setRejectedBy(dto.rejectedBy());
        consultation.setRejectReason(dto.reason());

        Consultation saved = consultationRepository.saveAndFlush(consultation);

        LOG.info("[REJECT] Consultation rejected successfully id={}", saved.getId());

        return saved;
    }


    @Transactional
    public Consultation submitConsultationResponse(
            Long consultationId,
            ConsultationResponseDTO consultationResponseDTO
    ) {

        LOG.info(
                "[ADD_RESPONSE] Consultation id={} responseBy={}",
                consultationId,
                consultationResponseDTO.responseBy()
        );

        Consultation consultation =
                consultationRepository.findById(consultationId)
                        .orElseThrow(() ->
                                new BadRequestAlertException(
                                        "Consultation not found",
                                        "consultation",
                                        "notfound"
                                )
                        );
        consultation.setResponseText(consultationResponseDTO.responseText());
        consultation.setResponseBy(consultationResponseDTO.responseBy());
        consultation.setResponseDate(Instant.now());
        consultation.setStatus(ConsultationStatus.READY);

        Consultation saved = consultationRepository.saveAndFlush(consultation);

        LOG.info("[ADD_RESPONSE] Response added successfully id={}", saved.getId());

        return saved;
    }

    @Transactional
    public ConsultationSubmitResultDTO submitConsultations(
            ConsultationSubmitRequestDTO consultationSubmitRequestDTO
    ) {

        LOG.info("[SUBMIT] consultationIds={}", consultationSubmitRequestDTO.consultationIds());

        List<Long> consultationIds = consultationSubmitRequestDTO.consultationIds();

        List<Consultation> consultations =
                consultationRepository.findAllById(consultationIds);

        List<ConsultationSubmitErrorDTO> errors =
                consultations.stream()
                        .filter(consultation ->
                                consultation.getResponseText() == null
                                        || consultation.getResponseText().isBlank()
                                        || consultation.getStatus() != ConsultationStatus.READY
                        )
                        .map(consultation -> new ConsultationSubmitErrorDTO(
                                consultation.getId(),
                                consultation.getConsultationNumber(),
                                (consultation.getResponseText() == null
                                        || consultation.getResponseText().isBlank())
                                        ? "Consultation has no response"
                                        : "Consultation cannot be submitted with status "
                                        + consultation.getStatus()
                        ))
                        .toList();

        List<Consultation> consultationsToSubmit =
                consultations.stream()
                        .filter(consultation ->
                                consultation.getResponseText() != null
                                        && !consultation.getResponseText().isBlank()
                                        && consultation.getStatus() == ConsultationStatus.READY
                        )
                        .toList();

        consultationsToSubmit.forEach(
                consultation -> consultation.setStatus(ConsultationStatus.SUBMITTED)
        );

        consultationRepository.saveAll(consultationsToSubmit);

        LOG.info("[SUBMIT] submittedCount={} errorsCount={}",
                consultationsToSubmit.size(),
                errors.size()
        );

        return new ConsultationSubmitResultDTO(
                consultationsToSubmit.size(),
                errors
        );
    }


}
