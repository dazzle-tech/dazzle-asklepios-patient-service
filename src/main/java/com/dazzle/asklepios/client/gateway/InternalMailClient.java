package com.dazzle.asklepios.client.gateway;


import com.dazzle.asklepios.client.gateway.dto.PatientCreatePasswordMailDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "gatewayServiceClient",
        url = "${service.asklepios-gateway-service-url}",
        configuration = SetupServiceFeignConfig.class
)public interface InternalMailClient {

    @PostMapping("/api/internal/mail/patient-create-password")
    ResponseEntity<Void> sendPatientCreatePasswordMail(@RequestBody PatientCreatePasswordMailDTO dto);
}
