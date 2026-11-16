package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.EncounterAttachmentClient;
import com.dazzle.asklepios.client.setup.dto.EncounterAttachmentDTO;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalSupportingInfo;
import com.dazzle.asklepios.service.AttachmentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The files attached on the Dental Procedures screen go with the claim. */
class DentalAttachmentSupportingInfoMapperTest {

    private static final long TEN_MB = 10L * 1024 * 1024;
    private static final byte[] FILE = {1, 2, 3, 4};

    private EncounterAttachmentClient client;
    private AttachmentStorageService storage;
    private DentalAttachmentSupportingInfoMapper mapper;
    private PatientEncounter encounter;

    @BeforeEach
    void setUp() {
        client = mock(EncounterAttachmentClient.class);
        storage = mock(AttachmentStorageService.class);
        mapper = new DentalAttachmentSupportingInfoMapper(client, storage);
        encounter = new PatientEncounter();
        encounter.setId(529L);
        encounter.setEncounterDate(LocalDate.of(2026, 11, 14));
        when(storage.getBytes(anyString())).thenReturn(FILE);
    }

    @Test
    void theDentalProcedureFilesFollowTheSupportingInfo() {
        attachments(
                attachment(1L, "xray.jpg", "image/jpeg", 4L),
                attachment(2L, "report.pdf", "application/pdf", 4L)
        );

        List<WaseelApprovalSupportingInfo> result = mapper.withDentalAttachments(List.of(info(1), info(2)), encounter);

        assertThat(result).hasSize(4);
        WaseelApprovalSupportingInfo xray = result.get(2);
        assertThat(xray.sequence()).isEqualTo(3);
        assertThat(xray.category()).isEqualTo("attachment");
        assertThat(xray.attachmentName()).isEqualTo("xray.jpg");
        assertThat(xray.attachmentType()).isEqualTo("image/jpeg");
        assertThat(xray.attachment()).isEqualTo(Base64.getEncoder().encodeToString(FILE));
        assertThat(xray.attachmentDate()).isEqualTo("2026-11-14");
        assertThat(result.get(3).sequence()).isEqualTo(4);
        assertThat(result.get(3).attachmentName()).isEqualTo("report.pdf");
        assertThat(result.get(3).attachmentType()).isEqualTo("application/pdf");
    }

    @Test
    void aVisitWithoutDentalProcedureFilesKeepsItsSupportingInfo() {
        attachments();
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(info(1));

        assertThat(mapper.withDentalAttachments(supportingInfo, encounter)).isSameAs(supportingInfo);
    }

    @Test
    void filesNphiesDoesNotAcceptAreLeftOut() {
        attachments(
                attachment(1L, "scan.png", "image/png", 4L),
                attachment(2L, "notes.docx", "application/msword", 4L),
                attachment(3L, "huge.pdf", "application/pdf", TEN_MB + 1),
                attachment(4L, "photo.JPEG", "application/octet-stream", 4L)
        );

        List<WaseelApprovalSupportingInfo> result = mapper.withDentalAttachments(List.of(info(1)), encounter);

        assertThat(result).extracting(WaseelApprovalSupportingInfo::attachmentName).containsExactly(null, "photo.JPEG");
    }

    @Test
    void filesStopBeforeTheClaimPassesTenMegabytes() {
        byte[] sixMegabytes = new byte[6 * 1024 * 1024];
        when(storage.getBytes("key-1")).thenReturn(sixMegabytes);
        when(storage.getBytes("key-2")).thenReturn(sixMegabytes);
        attachments(
                attachment(1L, "first.pdf", "application/pdf", (long) sixMegabytes.length),
                attachment(2L, "second.pdf", "application/pdf", (long) sixMegabytes.length)
        );

        List<WaseelApprovalSupportingInfo> result = mapper.withDentalAttachments(List.of(info(1)), encounter);

        assertThat(result).extracting(WaseelApprovalSupportingInfo::attachmentName).containsExactly(null, "first.pdf");
    }

    @Test
    void anUnreachableSetupServiceStillSendsTheClaim() {
        when(client.listBySource(anyLong(), anyString(), any())).thenThrow(new RuntimeException("setup down"));
        List<WaseelApprovalSupportingInfo> supportingInfo = List.of(info(1));

        assertThat(mapper.withDentalAttachments(supportingInfo, encounter)).isSameAs(supportingInfo);
    }

    private void attachments(EncounterAttachmentDTO... attachments) {
        when(client.listBySource(529L, "DENTAL_PROCEDURE_ATTACHMENT", null)).thenReturn(List.of(attachments));
    }

    private static EncounterAttachmentDTO attachment(Long id, String name, String mime, Long bytes) {
        return new EncounterAttachmentDTO(id, 529L, "key-" + id, name, mime, bytes, null,
                "DENTAL_PROCEDURE_ATTACHMENT", 17L, Instant.parse("2026-11-14T09:00:00Z"));
    }

    private static WaseelApprovalSupportingInfo info(int sequence) {
        return new WaseelApprovalSupportingInfo(sequence, "chief-complaint", null, null, null, "pain",
                null, null, null, null, null, null);
    }
}
