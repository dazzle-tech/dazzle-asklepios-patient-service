package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "setupServiceClient",
        url = "${service.asklepios-setup-service-url}",
        configuration = SetupServiceFeignConfig.class
)
public interface PractitionerClient {

    @GetMapping("/api/setup/practitioner/{id}")
    ResponseEntity<Void> existPractitioner(@PathVariable("id") Long id);

    @GetMapping("/api/setup/practitioner/{id}")
    PractitionerDTO getPractitioner(@PathVariable("id") Long id);

    @GetMapping("/api/setup/practitioners/by-login/{login}")
    PractitionerDTO getPractitionerByLogin(@PathVariable("login") String login);

    @GetMapping("/api/setup/practitioner/resolve")
    PractitionerDTO resolvePractitioner(
            @RequestParam(value = "practitionerId", required = false) Long practitionerId,
            @RequestParam(value = "login", required = false) String login
    );

    @GetMapping("/api/setup/practitioner/{practitionerId}/departments/{departmentId}/access")
    Boolean hasDepartmentAccess(@PathVariable Long practitionerId, @PathVariable Long departmentId);
}