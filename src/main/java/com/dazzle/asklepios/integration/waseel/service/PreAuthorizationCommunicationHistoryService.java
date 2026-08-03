package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.PreAuthorizationAttachment;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.PreAuthorizationTrack;
import com.dazzle.asklepios.integration.waseel.dto.PreAuthorizationCommunicationHistoryResponse;
import com.dazzle.asklepios.repository.PreAuthorizationAttachmentRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.repository.PreAuthorizationTrackRepository;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreAuthorizationCommunicationHistoryService {

    public static final String TRACK_TYPE_COMMUNICATION = "COMMUNICATION";

    private final PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    private final PreAuthorizationTrackRepository trackRepository;
    private final PreAuthorizationAttachmentRepository attachmentRepository;
    private final ObjectMapper objectMapper;

    public List<PreAuthorizationCommunicationHistoryResponse> list(Long preAuthorizationId) {
        requirePreAuthorization(preAuthorizationId);

        Map<Long, PreAuthorizationAttachment> attachmentsById = attachmentRepository
                .findByPreAuthorizationIdAndDeletedAtIsNullOrderByCreatedDateDesc(preAuthorizationId)
                .stream()
                .collect(Collectors.toMap(
                        PreAuthorizationAttachment::getId,
                        Function.identity(),
                        (a, b) -> a
                ));

        return trackRepository
                .findByPreAuthorization_IdAndTrackTypeOrderByCreatedDateDesc(
                        preAuthorizationId,
                        TRACK_TYPE_COMMUNICATION
                )
                .stream()
                .map(track -> toHistory(track, attachmentsById))
                .toList();
    }

    public long count(Long preAuthorizationId) {
        return trackRepository.countByPreAuthorization_IdAndTrackType(
                preAuthorizationId,
                TRACK_TYPE_COMMUNICATION
        );
    }

    private PreAuthorizationCommunicationHistoryResponse toHistory(
            PreAuthorizationTrack track,
            Map<Long, PreAuthorizationAttachment> attachmentsById
    ) {
        Long communicationId = null;
        List<PreAuthorizationCommunicationHistoryResponse.CommunicationPayloadHistory> payloads =
                new ArrayList<>();

        try {
            if (track.getResponseJson() != null && !track.getResponseJson().isBlank()) {
                JsonNode response = objectMapper.readTree(track.getResponseJson());
                if (response.hasNonNull("communicationId")) {
                    communicationId = response.get("communicationId").asLong();
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse communication responseJson trackId={}", track.getId(), ex);
        }

        try {
            if (track.getRequestJson() != null && !track.getRequestJson().isBlank()) {
                JsonNode request = objectMapper.readTree(track.getRequestJson());
                JsonNode payloadNodes = request.get("payloads");
                if (payloadNodes != null && payloadNodes.isArray()) {
                    for (JsonNode payloadNode : payloadNodes) {
                        payloads.add(toPayloadHistory(payloadNode, attachmentsById));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to parse communication requestJson trackId={}", track.getId(), ex);
        }

        if (payloads.isEmpty() && track.getMessage() != null && !track.getMessage().isBlank()) {
            payloads.add(
                    new PreAuthorizationCommunicationHistoryResponse.CommunicationPayloadHistory(
                            "TEXT",
                            track.getMessage(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    )
            );
        }

        return new PreAuthorizationCommunicationHistoryResponse(
                track.getId(),
                track.getTrackType(),
                track.getStatus(),
                track.getOutcome(),
                track.getMessage(),
                communicationId,
                track.getTransactionId(),
                track.getApprovalResponseId(),
                track.getCreatedDate(),
                track.getCreatedBy(),
                payloads
        );
    }

    private PreAuthorizationCommunicationHistoryResponse.CommunicationPayloadHistory toPayloadHistory(
            JsonNode payloadNode,
            Map<Long, PreAuthorizationAttachment> attachmentsById
    ) {
        String payloadValue = text(payloadNode.get("payloadValue"));
        String attachmentName = text(payloadNode.get("attachmentName"));
        String attachmentType = text(payloadNode.get("attachmentType"));
        Long claimItemId = asLong(payloadNode.get("claimItemId"));
        Long attachmentId = asLong(payloadNode.get("attachmentId"));

        Long sizeBytes = null;
        Boolean isSentToWaseel = null;
        String downloadUrl = null;

        if (attachmentId != null) {
            PreAuthorizationAttachment attachment = attachmentsById.get(attachmentId);
            if (attachment != null) {
                if (attachmentName == null) {
                    attachmentName = attachment.getFilename();
                }
                if (attachmentType == null) {
                    attachmentType = attachment.getMimeType();
                }
                sizeBytes = attachment.getSizeBytes();
                isSentToWaseel = attachment.getIsSentToWaseel();
                downloadUrl = "/api/patient/internal/waseel/pre-authorizations/attachments/"
                        + attachment.getId()
                        + "/file";
            }
        }

        boolean hasText = payloadValue != null && !payloadValue.isBlank();
        boolean hasAttachment = attachmentId != null
                || (attachmentName != null && !attachmentName.isBlank());
        String contentType = hasText && hasAttachment
                ? "TEXT_AND_ATTACHMENT"
                : hasAttachment ? "ATTACHMENT" : "TEXT";

        return new PreAuthorizationCommunicationHistoryResponse.CommunicationPayloadHistory(
                contentType,
                payloadValue,
                claimItemId,
                attachmentId,
                attachmentName,
                attachmentType,
                sizeBytes,
                isSentToWaseel,
                downloadUrl
        );
    }

    private PreAuthorizationRequest requirePreAuthorization(Long preAuthorizationId) {
        return preAuthorizationRequestRepository.findById(preAuthorizationId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Pre-authorization not found with id " + preAuthorizationId,
                        "preAuthorization",
                        "notfound"
                ));
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText();
        return value != null && !value.isBlank() ? value : null;
    }

    private static Long asLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.valueOf(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
