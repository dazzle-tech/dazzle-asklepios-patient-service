package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.DepartmentClient;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import org.springframework.stereotype.Service;

@Service
public class DepartmentHelper {

    private final DepartmentClient departmentClient;

    public DepartmentHelper(DepartmentClient departmentClient) {
        this.departmentClient = departmentClient;
    }


    public void validateDepartmentExists(Long departmentId) {
        try {
            departmentClient.existsDepartment(departmentId);
        } catch (feign.FeignException.NotFound ex) {
            throw new NotFoundAlertException(
                    "Department not found: " + departmentId,
                    "department",
                    "notfound"
            );
        }
    }
}
