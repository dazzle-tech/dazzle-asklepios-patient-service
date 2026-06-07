package com.dazzle.asklepios.client.setup;


import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.config.SetupServiceFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "setupServiceClient",url = "${service.asklepios-setup-service-url}" , configuration = SetupServiceFeignConfig.class)
public interface DepartmentClient {

    @GetMapping("/api/setup/department/{id}")
    ResponseEntity<Void> existsDepartment(@PathVariable("id") Long id);

    @GetMapping("/api/setup/department/{id}")
    DepartmentDTO getDepartment(@PathVariable("id") Long id);

    @GetMapping("/api/setup/department/bookable-departments")
    List<DepartmentDTO> getBookableDepartments();
}
