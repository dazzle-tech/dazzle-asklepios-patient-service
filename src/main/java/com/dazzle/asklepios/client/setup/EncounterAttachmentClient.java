package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.EncounterAttachmentDTO;
import com.dazzle.asklepios.client.setup.dto.EncounterAttachmentUploadDTO;
import com.dazzle.asklepios.config.SetupMultipartFeignConfig;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@FeignClient(
        name = "setupServiceClient",
        contextId = "encounterAttachmentClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = {SetupServiceFeignConfig.class, SetupMultipartFeignConfig.class}
)
public interface EncounterAttachmentClient {

    @PostMapping(value = "/api/setup/encounters/{encounterId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    EncounterAttachmentUploadDTO upload(
            @PathVariable("encounterId") Long encounterId,
            @RequestPart("file") MultipartFile file,
            @RequestPart("type") String type,
            @RequestPart("details") String details,
            @RequestPart("source") String source,
            @RequestPart("sourceId") String sourceId
    );

    /** Active (not deleted) attachments of a visit uploaded from one screen, newest first; every row of it when sourceId is null. */
    @GetMapping("/api/setup/encounters/attachments/by-encounterIdAndSource/{encounterId}/{source}")
    List<EncounterAttachmentDTO> listBySource(
            @PathVariable("encounterId") Long encounterId,
            @PathVariable("source") String source,
            @RequestParam(value = "sourceId", required = false) Long sourceId
    );
}
