package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.EncounterAttachmentUploadDTO;
import com.dazzle.asklepios.config.SetupMultipartFeignConfig;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

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
}
