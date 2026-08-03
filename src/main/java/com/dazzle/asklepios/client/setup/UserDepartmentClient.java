package com.dazzle.asklepios.client.setup;

import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "setupServiceClient", url = "${service.asklepios-setup-service-url}", configuration = SetupServiceFeignConfig.class)
public interface UserDepartmentClient {

    @GetMapping("/api/setup/user-department/department/{departmentId}/users")
    List<UserDTO> getUsersForDepartment(@PathVariable("departmentId") Long departmentId);

    @GetMapping("/api/setup/user-department/department/internalJob/{departmentId}/users")
    List<UserDTO> getUsersForDepartmentInternal(@PathVariable("departmentId") Long departmentId);

    @GetMapping("/api/setup/user-department/department/{departmentId}/physician-users")
    List<UserDTO> getPhysicianUsersForDepartment(@PathVariable("departmentId") Long departmentId);

    @GetMapping("/api/setup/user-department/department/{departmentId}/nurse-users")
    List<UserDTO> getNurseUsersForDepartment(@PathVariable("departmentId") Long departmentId);
}