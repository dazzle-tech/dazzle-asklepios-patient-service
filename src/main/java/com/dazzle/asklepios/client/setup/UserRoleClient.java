package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.FacilityUserDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "setupServiceClient",
    url = "${service.asklepios-setup-service-url}",
    configuration = SetupServiceFeignConfig.class
)
public interface UserRoleClient {

    /**
     * Users who have any role belonging to the facility
     * (same membership rule used by facility login).
     */
    @GetMapping("/api/setup/user-role/users/by-facility/{facilityId}")
    List<FacilityUserDTO> getUsersByFacility(@PathVariable("facilityId") Long facilityId);
}
