package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationCareTeam;
import com.dazzle.asklepios.domain.PreAuthorizationDiagnosis;
import com.dazzle.asklepios.domain.PreAuthorizationItem;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.PreAuthorizationSupportingInfo;
import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.approval.ApprovalResponse;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalCareTeam;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalDiagnosis;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalEligibilitySnapshot;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalRequest;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationCareTeamRepository;
import com.dazzle.asklepios.repository.PreAuthorizationDiagnosisRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.repository.PreAuthorizationSupportingInfoRepository;
import com.dazzle.asklepios.repository.PreAuthorizationTrackRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PreAuthorizationSubmissionService {

    private final ApprovalRequestBuilderService approvalRequestBuilderService;
    private final ApprovalEligibilitySnapshotService snapshotService;
    private final WaseelApprovalService waseelApprovalService;
    private final EligibilityRequestResolverService eligibilityRequestResolverService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationItemRepository preAuthorizationItemRepository;
    private final PreAuthorizationDiagnosisRepository preAuthorizationDiagnosisRepository;
    private final PreAuthorizationCareTeamRepository preAuthorizationCareTeamRepository;
    private final PreAuthorizationSupportingInfoRepository preAuthorizationSupportingInfoRepository;
    private final PreAuthorizationTrackRepository preAuthorizationTrackRepository;

    private final WaseelApiProperties waseelApiProperties;
    private final ObjectMapper objectMapper;

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public ApprovalResponse submitIfRequired(Long encounterId) {
        if (!encounterInsuranceEligibilityService.isInsuranceEncounter(encounterId)) {
            log.debug(
                    "Skipping pre-authorization submission for encounterId={} — not an insurance encounter",
                    encounterId
            );
            return null;
        }

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        Long eligibilityRequestId =
                eligibilityRequestResolverService.resolveLatestSuccessfulEligibilityId(encounter);

        return submitIfRequired(eligibilityRequestId, encounterId);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class
    )
    public ApprovalResponse submitIfRequired(Long eligibilityRequestId, Long encounterId) {
        List<PatientServiceAndProduct> pendingItems =
                patientServiceAndProductRepository.findByEncounterIdAndPreAuthorizationStatus(
                        encounterId,
                        PreAuthorizationStatus.PENDING_APPROVAL
                );

        if (pendingItems == null || pendingItems.isEmpty()) {
            log.info(
                    "[PREAUTH_SUBMIT] No PENDING_APPROVAL items for encounterId={} — skipping Waseel submission",
                    encounterId
            );
            return null;
        }

        log.info(
                "[PREAUTH_SUBMIT] Submitting pre-authorization to Waseel. encounterId={} pendingItemCount={}",
                encounterId,
                pendingItems.size()
        );

        PatientEncounter encounter = patientEncounterRepository.findById(encounterId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "Encounter not found",
                        "preAuthorization",
                        "encounter.notFound"
                ));

        WaseelApprovalRequest request =
                approvalRequestBuilderService.buildRequest(eligibilityRequestId, encounterId);

        WaseelApprovalEligibilitySnapshot snapshot =
                snapshotService.buildSnapshot(eligibilityRequestId);

        validateSnapshot(snapshot);

        String requestJson = toJson(request);

        PreAuthorizationRequest preAuthorization =
                savePreAuthorizationRequest(
                        encounter,
                        request,
                        snapshot,
                        requestJson
                );

        saveDetails(preAuthorization, request, pendingItems);

        try {
            logPreAuthorizationRequest(request, requestJson);

            ApprovalResponse response = waseelApprovalService.requestApproval(request);
            String responseJson = toJson(response);

            updatePreAuthorizationSuccess(preAuthorization, response, responseJson);
            saveTrack(preAuthorization, "SUBMIT", requestJson, responseJson, response);
            updateItemsAfterResponse(pendingItems, response);

            return response;

        } catch (RestClientException ex) {
            updatePreAuthorizationFailure(preAuthorization, ex.getMessage());
            saveTrackFailure(preAuthorization, "SUBMIT", requestJson, ex.getMessage());

            throw new BadRequestAlertException(
                    "Failed to submit pre-authorization to Waseel: " + ex.getMessage(),
                    "preAuthorization",
                    "waseel.submit.failed"
            );
        }
    }

    private void logPreAuthorizationRequest(
            WaseelApprovalRequest request,
            String requestJson
    ) {
        log.info("============== PRE AUTH CARE TEAM ==============");

        if (request.careTeam() != null && !request.careTeam().isEmpty()) {
            request.careTeam().forEach(careTeam -> {
                log.info("practitionerName={}", careTeam.practitionerName());
                log.info("physicianCode={}", careTeam.physicianCode());
                log.info("practitionerRole={}", careTeam.practitionerRole());
                log.info("careTeamRole={}", careTeam.careTeamRole());
                log.info("speciality={}", careTeam.speciality());
                log.info("specialityCode={}", careTeam.specialityCode());
                log.info("qualificationCode={}", careTeam.qualificationCode());
            });
        } else {
            log.info("careTeam is empty");
        }

        log.info("============== PRE AUTH ITEMS ==============");

        if (request.items() != null && !request.items().isEmpty()) {
            request.items().forEach(item -> {
                log.info("sequence={}", item.sequence());
                log.info("type={}", item.type());
                log.info("itemCode={}", item.itemCode());
                log.info("itemDescription={}", item.itemDescription());
                log.info("quantity={}", item.quantity());
                log.info("unitPrice={}", item.unitPrice());
                log.info("net={}", item.net());
                log.info("patientShare={}", item.patientShare());
                log.info("payerShare={}", item.payerShare());
                log.info("careTeamSequence={}", item.careTeamSequence());
                log.info("diagnosisSequence={}", item.diagnosisSequence());
            });
        } else {
            log.info("items is empty");
        }

        System.out.println("WASEEL REQUEST JSON for PRE-AUth = " + requestJson);
        log.info("============== REQUEST JSON ==============");
        log.info(requestJson);
        log.info("=========================================");
    }

    private void validateSnapshot(WaseelApprovalEligibilitySnapshot snapshot) {
        if (snapshot == null) {
            throw new BadRequestAlertException(
                    "Eligibility snapshot not found",
                    "preAuthorization",
                    "eligibility.snapshotNotFound"
            );
        }

        if (snapshot.patientInsuranceId() == null) {
            throw new BadRequestAlertException(
                    "Patient insurance not found in eligibility",
                    "preAuthorization",
                    "patientInsurance.notFound"
            );
        }
    }

    private PreAuthorizationRequest savePreAuthorizationRequest(
            PatientEncounter encounter,
            WaseelApprovalRequest request,
            WaseelApprovalEligibilitySnapshot snapshot,
            String requestJson
    ) {
        PreAuthorizationRequest preAuthorization = PreAuthorizationRequest.builder()
                .patientId(encounter.getPatient().getId())
                .encounterId(encounter.getId())
                .patientInsuranceId(snapshot.patientInsuranceId())
                .providerId(firstNonBlank(snapshot.providerId(), waseelApiProperties.providerId()))
                .providerNphiesId(firstNonBlank(waseelApiProperties.nphiesId(), snapshot.providerId()))
                .eligibilityResponseId(firstNonBlank(
                        snapshot.eligibilityResponseId(),
                        request.preAuthorizationInfo().eligibilityResponseId()))
                .eligibilityResponseUrl(firstNonBlank(
                        snapshot.eligibilityResponseUrl(),
                        request.preAuthorizationInfo().eligibilityResponseUrl()))
                .eligibilityOfflineId(request.preAuthorizationInfo().eligibilityOfflineId())
                .eligibilityOfflineDate(request.preAuthorizationInfo().eligibilityOfflineDate())
                .dateOrdered(request.preAuthorizationInfo().dateOrdered())
                .payeeId(request.preAuthorizationInfo().payeeId())                .payeeType(request.preAuthorizationInfo().payeeType())
                .preauthType(request.preAuthorizationInfo().type())
                .preauthSubType(request.preAuthorizationInfo().subType())
                .episodeId(request.preAuthorizationInfo().episodeId())
                .prescription(request.preAuthorizationInfo().prescription())
                .transfer(Boolean.TRUE.equals(request.transfer()))
                .isNewBorn(Boolean.TRUE.equals(request.isNewBorn()))
                .isCancelled(Boolean.FALSE)
                .destinationId(firstNonBlank(snapshot.destinationId(), request.destinationId()))
                .encounterStatus(request.encounter() == null ? null : request.encounter().status())
                .encounterClass(request.encounter() == null ? null : request.encounter().encounterClass())
                .serviceType(request.encounter() == null ? null : request.encounter().serviceType())
                .serviceEventType(request.encounter() == null ? null : request.encounter().serviceEventType())
                .serviceProvider(request.encounter() == null ? null : request.encounter().serviceProvider())
                .encounterStartDate(request.encounter() == null ? null : request.encounter().startDate())
                .encounterEndDate(request.encounter() == null ? null : request.encounter().periodEnd())
                .totalNet(request.totalNet() == null ? BigDecimal.ZERO : request.totalNet())
                .status("SUBMITTING")
                .requestJson(requestJson)
                .build();

        return preAuthorizationRequestRepository.saveAndFlush(preAuthorization);
    }

    private void saveDetails(
            PreAuthorizationRequest preAuthorization,
            WaseelApprovalRequest request,
            List<PatientServiceAndProduct> sourceItems
    ) {
        saveSupportingInfo(preAuthorization, request.supportingInfo());
        saveDiagnosis(preAuthorization, request.diagnosis());
        saveCareTeam(preAuthorization, request.careTeam());
        saveItems(preAuthorization, request.items(), sourceItems);
    }

    private void saveSupportingInfo(
            PreAuthorizationRequest preAuthorization,
            List<WaseelApprovalSupportingInfo> supportingInfo
    ) {
        if (supportingInfo == null || supportingInfo.isEmpty()) {
            return;
        }

        List<PreAuthorizationSupportingInfo> entities = supportingInfo.stream()
                .map(info -> PreAuthorizationSupportingInfo.builder()
                        .preAuthorization(preAuthorization)
                        .sequence(info.sequence())
                        .category(info.category())
                        .code(info.code())
                        .fromDate(parseDate(info.fromDate()))
                        .toDate(parseDate(info.toDate()))
                        .value(info.value())
                        .reason(info.reason())
                        .attachment(info.attachment())
                        .attachmentName(info.attachmentName())
                        .attachmentType(info.attachmentType())
                        .unit(info.unit())
                        .attachmentDate(parseDate(info.attachmentDate()))
                        .build())
                .toList();

        preAuthorizationSupportingInfoRepository.saveAll(entities);
    }

    private void saveDiagnosis(
            PreAuthorizationRequest preAuthorization,
            List<WaseelApprovalDiagnosis> diagnosis
    ) {
        if (diagnosis == null || diagnosis.isEmpty()) {
            return;
        }

        List<PreAuthorizationDiagnosis> entities = diagnosis.stream()
                .map(d -> PreAuthorizationDiagnosis.builder()
                        .preAuthorization(preAuthorization)
                        .sequence(d.sequence())
                        .diagnosisCode(d.diagnosisCode())
                        .diagnosisDescription(d.diagnosisDescription())
                        .diagnosisType(d.type())
                        .onAdmission(null)
                        .build())
                .toList();

        preAuthorizationDiagnosisRepository.saveAll(entities);
    }

    private void saveCareTeam(
            PreAuthorizationRequest preAuthorization,
            List<WaseelApprovalCareTeam> careTeam
    ) {
        if (careTeam == null || careTeam.isEmpty()) {
            return;
        }

        List<PreAuthorizationCareTeam> entities = careTeam.stream()
                .map(c -> PreAuthorizationCareTeam.builder()
                        .preAuthorization(preAuthorization)
                        .sequence(c.sequence())
                        .practitionerName(c.practitionerName())
                        .physicianCode(c.physicianCode())
                        .practitionerRole(c.practitionerRole())
                        .careTeamRole(c.careTeamRole())
                        .speciality(c.speciality())
                        .specialityCode(c.specialityCode())
                        .qualificationCode(c.qualificationCode())
                        .build())
                .toList();

        preAuthorizationCareTeamRepository.saveAll(entities);
    }

    private void saveItems(
            PreAuthorizationRequest preAuthorization,
            List<WaseelApprovalItem> items,
            List<PatientServiceAndProduct> sourceItems
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }

        List<PatientServiceAndProduct> filteredSourceItems = sourceItems == null
                ? List.of()
                : sourceItems.stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                .toList();

        List<PreAuthorizationItem> entities = items.stream()
                .map(item -> {
                    PatientServiceAndProduct source = findSourceItem(item.sequence(), filteredSourceItems);

                    return PreAuthorizationItem.builder()
                            .preAuthorizationId(preAuthorization.getId())
                            .sequence(item.sequence())
                            .itemType(item.type())
                            .itemCode(item.itemCode())
                            .itemDescription(item.itemDescription())
                            .nonStandardCode(item.nonStandardCode())
                            .nonStandardDesc(item.nonStandardDesc())
                            .isPackage(Boolean.TRUE.equals(item.isPackage()))
                            .isMaternity(Boolean.TRUE.equals(item.isMaternity()))
                            .bodySite(item.bodySite())
                            .subSite(item.subSite())
                            .quantity(
                                    item.quantity() == null
                                            ? BigDecimal.ZERO
                                            : BigDecimal.valueOf(item.quantity())
                            )
                            .quantityCode(item.quantityCode())
                            .unitPrice(item.unitPrice())
                            .discount(item.discount())
                            .factor(item.factor())
                            .taxPercent(item.taxPercent())
                            .tax(item.tax())
                            .patientSharePercent(item.patientSharePercent())
                            .patientShare(item.patientShare())
                            .payerShare(item.payerShare())
                            .net(item.net())
                            .startDate(item.startDate())
                            .endDate(item.endDate())
                            .invoiceNo(item.invoiceNo())
                            .brandMedicationId(source == null ? null : source.getBrandMedicationId())
                            .diagnosticTestId(source == null ? null : source.getDiagnosticTestId())
                            .serviceId(source == null ? null : source.getServiceId())
                            .procedureId(source == null ? null : source.getProcedureId())
                            .rawJson(toJson(item))
                            .build();
                })
                .toList();

        preAuthorizationItemRepository.saveAll(entities);
    }

    private PatientServiceAndProduct findSourceItem(
            Integer sequence,
            List<PatientServiceAndProduct> sourceItems
    ) {
        if (sequence == null || sourceItems == null || sourceItems.isEmpty()) {
            return null;
        }

        int index = sequence - 1;

        if (index < 0 || index >= sourceItems.size()) {
            return null;
        }

        return sourceItems.get(index);
    }

    private void updatePreAuthorizationSuccess(
            PreAuthorizationRequest preAuthorization,
            ApprovalResponse response,
            String responseJson
    ) {
        preAuthorization.setStatus(safe(response == null ? null : response.status(), "SUBMITTED"));
        preAuthorization.setOutcome(response == null ? null : response.outcome());
        preAuthorization.setDisposition(response == null ? null : response.disposition());
        preAuthorization.setMessage(response == null ? null : response.disposition());
        preAuthorization.setTransactionId(response == null ? null : response.transactionId());
        preAuthorization.setOutgoingTransactionId(response == null ? null : response.outgoingTransactionId());
        preAuthorization.setApprovalRequestId(
                response == null ? null : response.approvalRequestId());

        preAuthorization.setApprovalResponseId(
                response == null ? null : response.approvalResponseId());

        preAuthorization.setPreAuthRefNo(
                response == null ? null : response.preAuthRefNo());
        preAuthorization.setResponseJson(responseJson);

        preAuthorizationRequestRepository.saveAndFlush(preAuthorization);
    }

    private void updatePreAuthorizationFailure(
            PreAuthorizationRequest preAuthorization,
            String message
    ) {
        preAuthorization.setStatus("FAILED");
        preAuthorization.setMessage(message);
        preAuthorizationRequestRepository.saveAndFlush(preAuthorization);
    }

    private void saveTrack(
            PreAuthorizationRequest preAuthorization,
            String trackType,
            String requestJson,
            String responseJson,
            ApprovalResponse response
    ) {
        PreAuthorizationTrack track = PreAuthorizationTrack.builder()
                .preAuthorization(preAuthorization)
                .trackType(trackType)
                .status(response == null ? null : response.status())
                .outcome(response == null ? null : response.outcome())
                .message(response == null ? null : response.disposition())
                .disposition(response == null ? null : response.disposition())
                .transactionId(response == null ? null : response.transactionId())
                .outgoingTransactionId(response == null ? null : response.outgoingTransactionId())
                .approvalRequestId(response == null ? null : response.approvalRequestId())
                .approvalResponseId(response == null ? null : response.approvalResponseId())                .requestJson(requestJson)
                .responseJson(responseJson)
                .build();

        preAuthorizationTrackRepository.save(track);
    }

    private void saveTrackFailure(
            PreAuthorizationRequest preAuthorization,
            String trackType,
            String requestJson,
            String message
    ) {
        PreAuthorizationTrack track = PreAuthorizationTrack.builder()
                .preAuthorization(preAuthorization)
                .trackType(trackType)
                .status("FAILED")
                .message(message)
                .requestJson(requestJson)
                .build();

        preAuthorizationTrackRepository.save(track);
    }

    private void updateItemsAfterResponse(
            List<PatientServiceAndProduct> pendingItems,
            ApprovalResponse response
    ) {
        PreAuthorizationStatus status = mapResponseStatus(response);

        for (PatientServiceAndProduct item : pendingItems) {
            item.setPreAuthorizationStatus(status);
        }

        patientServiceAndProductRepository.saveAll(pendingItems);
    }

    private PreAuthorizationStatus mapResponseStatus(ApprovalResponse response) {
        if (response == null) {
            return PreAuthorizationStatus.PENDING_APPROVAL;
        }

        String status = response.status() == null ? "" : response.status().toLowerCase();
        String outcome = response.outcome() == null ? "" : response.outcome().toLowerCase();

        if (status.contains("approved")
                || outcome.contains("approved")
                || outcome.contains("complete")) {
            return PreAuthorizationStatus.APPROVED;
        }

        if (status.contains("rejected")
                || status.contains("denied")
                || outcome.contains("rejected")
                || outcome.contains("denied")
                || outcome.contains("error")) {
            return PreAuthorizationStatus.REJECTED;
        }

        return PreAuthorizationStatus.PENDING_APPROVAL;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return LocalDate.parse(value);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return "";
    }
}