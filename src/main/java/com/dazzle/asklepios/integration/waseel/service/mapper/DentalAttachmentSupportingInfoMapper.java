package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.EncounterAttachmentClient;
import com.dazzle.asklepios.client.setup.dto.EncounterAttachmentDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.service.AttachmentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Sends the files attached on the Dental Procedures screen of the visit (source
 * DENTAL_PROCEDURE_ATTACHMENT, set by that screen) with the claim, as NPHIES "attachment" supporting info.
 *
 * <ul>
 *   <li>NPHIES accepts pdf, jpg and jpeg up to 10 MB per file; any other file is left out.</li>
 *   <li>NPHIES rejects a claim above 10 MB, so the files stop once they would take it there.</li>
 *   <li>A file that cannot be read is left out: the claim is still sent.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DentalAttachmentSupportingInfoMapper {

    static final String DENTAL_PROCEDURE_SOURCE = "DENTAL_PROCEDURE_ATTACHMENT";

    private static final String ATTACHMENT_CATEGORY = "attachment";
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    /** Room for the attachments in a 10 MB claim, the rest of the claim kept aside. */
    private static final long MAX_ENCODED_ATTACHMENTS = 9L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 250;
    private static final ZoneId PROVIDER_ZONE = ZoneId.of("Asia/Riyadh");

    /** Waseel attachment types, by MIME type and by file extension. */
    private static final Map<String, String> TYPE_BY_MIME = Map.of(
            "application/pdf", "application/pdf",
            "image/jpeg", "image/jpeg",
            "image/jpg", "image/jpeg"
    );
    private static final Map<String, String> TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
    );

    private final EncounterAttachmentClient encounterAttachmentClient;
    private final AttachmentStorageService attachmentStorageService;

    /** The claim supporting info followed by the dental procedure attachments of the visit. */
    public List<WaseelApprovalSupportingInfo> withDentalAttachments(
            List<WaseelApprovalSupportingInfo> supportingInfo,
            PatientEncounter encounter
    ) {
        if (encounter == null || encounter.getId() == null) {
            return supportingInfo;
        }

        List<EncounterAttachmentDTO> attachments = dentalProcedureAttachments(encounter.getId());
        if (attachments.isEmpty()) {
            return supportingInfo;
        }

        List<WaseelApprovalSupportingInfo> result = new ArrayList<>(supportingInfo);
        int sequence = nextSequence(supportingInfo);
        long encodedBytes = encodedAttachmentBytes(supportingInfo);

        for (EncounterAttachmentDTO attachment : attachments) {
            String type = waseelType(attachment);
            if (type == null || attachment.sizeBytes() == null || attachment.sizeBytes() > MAX_FILE_BYTES) {
                log.info("[CLAIM_ATTACHMENT] Left out (type or size not accepted by NPHIES). encounterId={} attachmentId={} name={} mime={} bytes={}",
                        encounter.getId(), attachment.id(), attachment.filename(), attachment.mimeType(), attachment.sizeBytes());
                continue;
            }

            byte[] content = read(attachment);
            if (content == null || content.length == 0 || content.length > MAX_FILE_BYTES) {
                continue;
            }

            String encoded = Base64.getEncoder().encodeToString(content);
            if (encodedBytes + encoded.length() > MAX_ENCODED_ATTACHMENTS) {
                log.warn("[CLAIM_ATTACHMENT] Left out: the claim would exceed the 10 MB NPHIES limit. encounterId={} attachmentId={} name={}",
                        encounter.getId(), attachment.id(), attachment.filename());
                continue;
            }
            encodedBytes += encoded.length();

            result.add(new WaseelApprovalSupportingInfo(
                    sequence++,
                    ATTACHMENT_CATEGORY,
                    null,
                    null,
                    null,
                    null,
                    null,
                    encoded,
                    attachmentName(attachment),
                    type,
                    null,
                    attachmentDate(attachment, encounter).toString()
            ));
        }
        return result;
    }

    /** Every dental procedure of the visit, in one call. */
    private List<EncounterAttachmentDTO> dentalProcedureAttachments(Long encounterId) {
        try {
            List<EncounterAttachmentDTO> attachments =
                    encounterAttachmentClient.listBySource(encounterId, DENTAL_PROCEDURE_SOURCE, null);
            return attachments == null ? List.of() : attachments;
        } catch (RuntimeException exception) {
            log.warn("[CLAIM_ATTACHMENT] Dental procedure attachments could not be listed. encounterId={}", encounterId, exception);
            return List.of();
        }
    }

    private byte[] read(EncounterAttachmentDTO attachment) {
        try {
            return attachmentStorageService.getBytes(attachment.spaceKey());
        } catch (RuntimeException exception) {
            log.warn("[CLAIM_ATTACHMENT] Attachment could not be read. attachmentId={} key={}",
                    attachment.id(), attachment.spaceKey(), exception);
            return null;
        }
    }

    /** {@code null} when NPHIES does not accept the file. */
    private String waseelType(EncounterAttachmentDTO attachment) {
        String mime = attachment.mimeType() == null ? "" : attachment.mimeType().trim().toLowerCase(Locale.ROOT);
        String byMime = TYPE_BY_MIME.get(mime);
        if (byMime != null) {
            return byMime;
        }
        String name = attachment.filename() == null ? "" : attachment.filename().trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? null : TYPE_BY_EXTENSION.get(name.substring(dot + 1));
    }

    private String attachmentName(EncounterAttachmentDTO attachment) {
        String name = attachment.filename() == null || attachment.filename().isBlank()
                ? "dental-attachment-" + attachment.id()
                : attachment.filename().trim();
        return name.length() > MAX_NAME_LENGTH ? name.substring(name.length() - MAX_NAME_LENGTH) : name;
    }

    private LocalDate attachmentDate(EncounterAttachmentDTO attachment, PatientEncounter encounter) {
        if (attachment.createdDate() != null) {
            return attachment.createdDate().atZone(PROVIDER_ZONE).toLocalDate();
        }
        return encounter.getEncounterDate() != null ? encounter.getEncounterDate() : LocalDate.now(PROVIDER_ZONE);
    }

    private int nextSequence(List<WaseelApprovalSupportingInfo> supportingInfo) {
        return supportingInfo.stream()
                .map(WaseelApprovalSupportingInfo::sequence)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    /** What the attachments already in the claim (the investigation results report) take. */
    private long encodedAttachmentBytes(List<WaseelApprovalSupportingInfo> supportingInfo) {
        return supportingInfo.stream()
                .map(WaseelApprovalSupportingInfo::attachment)
                .filter(Objects::nonNull)
                .mapToLong(String::length)
                .sum();
    }
}
