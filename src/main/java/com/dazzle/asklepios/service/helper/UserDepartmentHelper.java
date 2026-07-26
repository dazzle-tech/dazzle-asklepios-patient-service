package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.UserDepartmentClient;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDepartmentHelper {

    private static final Logger LOG = LoggerFactory.getLogger(UserDepartmentHelper.class);

    private final UserDepartmentClient userDepartmentClient;

    public UserDepartmentHelper(UserDepartmentClient userDepartmentClient) {
        this.userDepartmentClient = userDepartmentClient;
    }

    public List<UserDTO> getUsersForDepartment(Long departmentId) {
        LOG.debug("Fetching users for department. departmentId={}", departmentId);

        try {
            return userDepartmentClient.getUsersForDepartment(departmentId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("user failed. Not found. departmentId={}", departmentId);

            throw new NotFoundAlertException(
                    "Users not found for department with id= " + departmentId,
                    "userDepartment",
                    "notfound"
            );
        }
    }

    public List<UserDTO> getUsersForDepartmentInternal(Long departmentId) {
        LOG.debug("Fetching users for department. departmentId={}", departmentId);

        try {
            return userDepartmentClient.getUsersForDepartmentInternal(departmentId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("user failed. Not found. departmentId={}", departmentId);

            throw new NotFoundAlertException(
                    "Users not found for department with id= " + departmentId,
                    "userDepartment",
                    "notfound"
            );
        }
    }

    public List<UserDTO> getPhysicianUsersForDepartment(Long departmentId) {
        LOG.debug("Fetching users for department. departmentId={}", departmentId);

        try {
            return userDepartmentClient.getPhysicianUsersForDepartment(departmentId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("user failed. Not found. departmentId={}", departmentId);

            throw new NotFoundAlertException(
                    "Users not found for department with id= " + departmentId,
                    "userDepartment",
                    "notfound"
            );
        }
    }

    public List<UserDTO> getNurseUsersForDepartment(Long departmentId) {
        LOG.debug("Fetching users for department. departmentId={}", departmentId);

        try {
            return userDepartmentClient.getNurseUsersForDepartment(departmentId);
        } catch (FeignException.NotFound ex) {
            LOG.warn("user failed. Not found. departmentId={}", departmentId);

            throw new NotFoundAlertException(
                    "Users not found for department with id= " + departmentId,
                    "userDepartment",
                    "notfound"
            );
        }
    }
}