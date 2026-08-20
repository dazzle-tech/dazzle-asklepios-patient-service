package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.notification.NotificationClient;
import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.OrganizationClient;
import com.dazzle.asklepios.client.setup.SystemConfigurationClient;
import com.dazzle.asklepios.client.setup.UserClient;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.FacilityDTO;
import com.dazzle.asklepios.client.setup.dto.OrganizationDefinitionDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.SystemConfigKey;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import com.dazzle.asklepios.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationHelper {

    private final NotificationClient notificationClient;
    private final UserDepartmentHelper userDepartmentHelper;
    private final UserClient userClient;
    private final DepartmentHelper departmentHelper;
    private final OrganizationClient organizationClient;
    private final SystemConfigurationClient systemConfigurationClient;
    private final FacilityHelper facilityHelper;

    public void sendNotification(
            Long facilityId,
            NotificationCode code,
            Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule,
            Map<String, Object> data,
            String relatedEntityType,
            Long relatedEntityId) {

        try {
            if (code == null) {
                log.warn("[NOTIFICATION] Skip notification because code is missing");
                return;
            }

            if (recipientsByRule == null || recipientsByRule.isEmpty()) {
                log.warn(
                        "[NOTIFICATION] Skip notification because no recipients. code={}, relatedEntityType={}, relatedEntityId={}",
                        code,
                        relatedEntityType,
                        relatedEntityId
                );
                return;
            }

            try {
                String logoUrl = systemConfigurationClient
                        .getResolvedValue(SystemConfigKey.SYSTEM_LOGO);

                if (logoUrl != null && !logoUrl.isBlank()) {
                    data.put("logo_url", logoUrl);
                }
            } catch (Exception e) {
                log.warn(
                        "[NOTIFICATION] Could not resolve SYSTEM_LOGO. Continuing without logo. error={}",
                        e.getMessage()
                );
            }

            Long loggedInFacilityId = getLoggedInFacility();

            if (loggedInFacilityId != null) {
                FacilityDTO facilityDTO = facilityHelper.getFacility(loggedInFacilityId);

                if (facilityDTO != null && facilityDTO.name() != null) {
                    data.put("logged_in_facility_name", facilityDTO.name());
                }
            }

            Map<String, Map<String, List<NotificationResolvedRecipientDTO>>> groupedRecipients =
                    groupRecipientsByLanguage(recipientsByRule);

            for (Map.Entry<String, Map<String, List<NotificationResolvedRecipientDTO>>> languageEntry
                    : groupedRecipients.entrySet()) {

                String language = languageEntry.getKey();

                NotificationCreateDTO dto = new NotificationCreateDTO(
                        facilityId,
                        code,
                        language,
                        null,
                        languageEntry.getValue(),
                        data,
                        relatedEntityType,
                        relatedEntityId
                );

                try {
                    log.debug(
                            "[NOTIFICATION] Creating notification. language={}, code={}, recipients={}",
                            language,
                            code,
                            languageEntry.getValue()
                    );

                    notificationClient.createNotification(dto);

                } catch (Exception e) {
                    log.warn(
                            "[NOTIFICATION] Failed notification. language={}, code={}, error={}",
                            language,
                            code,
                            e.getMessage()
                    );
                }
            }

        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed notification. code={}, error={}, recipientsByRule={}, data={}, relatedEntityType={}, relatedEntityId={}",
                    code,
                    e.getMessage(),
                    recipientsByRule,
                    data,
                    relatedEntityType,
                    relatedEntityId
            );
        }
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> resolveRecipients(Long departmentId, String login, String createdByLogin, Patient patient, PractitionerDTO practitionerDTO, Boolean isScheduleNotification) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        // Department users
        if (departmentId != null) {
            List<NotificationResolvedRecipientDTO> departmentUsers =
                    !isScheduleNotification ? buildDepartmentUserRecipients(departmentId, organizationDefinitionDTO) : buildDepartmentUserRecipientsForScheduledNotification(departmentId, organizationDefinitionDTO);

            if (!departmentUsers.isEmpty()) {
                recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
            }

            List<NotificationResolvedRecipientDTO> physicianDepartmentUsers =
                    buildPhysicianDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

            if (!departmentUsers.isEmpty()) {
                recipientsByRule.put("PHYSICIAN_DEPARTMENT_USERS", physicianDepartmentUsers);
            }

            List<NotificationResolvedRecipientDTO> nurseDepartmentUsers =
                    buildNurseDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

            if (!departmentUsers.isEmpty()) {
                recipientsByRule.put("NURSE_DEPARTMENT_USERS", nurseDepartmentUsers);
            }
        }

        // Current user
        if (login != null && !login.isBlank()) {
            NotificationResolvedRecipientDTO currentUser =
                    buildCurrentUserRecipient(login, organizationDefinitionDTO);

            if (currentUser != null) {
                recipientsByRule.put("CURRENT_USER", List.of(currentUser));
            }

            NotificationResolvedRecipientDTO currentUserPhoneRecipient =
                    buildCurrentUserPhoneRecipient(login, organizationDefinitionDTO);

            if (currentUserPhoneRecipient != null) {
                recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
            }
        }

        // Created by user
        if (createdByLogin != null && !createdByLogin.isBlank()) {
            NotificationResolvedRecipientDTO createdByUser =
                    buildCreatedByUserRecipient(createdByLogin, organizationDefinitionDTO);

            if (createdByUser != null) {
                recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
            }

            NotificationResolvedRecipientDTO createdByUserPhoneRecipient =
                    buildCreatedByUserPhoneRecipient(createdByLogin, organizationDefinitionDTO);

            if (createdByUserPhoneRecipient != null) {
                recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
            }
        }

        // Patient
        if (patient != null) {
            NotificationResolvedRecipientDTO patientEmailRecipient =
                    buildPatientEmailRecipient(patient, organizationDefinitionDTO);

            if (patientEmailRecipient != null) {
                recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
            }

            NotificationResolvedRecipientDTO patientPhoneRecipient =
                    buildPatientPhoneRecipient(patient, organizationDefinitionDTO);

            if (patientPhoneRecipient != null) {
                recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
            }
            NotificationResolvedRecipientDTO patientPushRecipient =
                    buildPatientPushRecipient(patient, organizationDefinitionDTO);

            if (patientPushRecipient != null) {
                recipientsByRule.put("PATIENT_USER", List.of(patientPushRecipient));
            }
        }

        // Practitioner
        if (practitionerDTO != null) {
            UserDTO practitionerUser = null;
            if (practitionerDTO.userId() != null) {
                practitionerUser = userClient.getUserByUserId(practitionerDTO.userId());
            }
            NotificationResolvedRecipientDTO practitionerEmailRecipient =
                    buildPractitionerEmailRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

            if (practitionerEmailRecipient != null) {
                recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
            }

            NotificationResolvedRecipientDTO practitionerUserRecipient =
                    buildPractitionerUserRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

            if (practitionerUserRecipient != null) {
                recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
            }
            NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                    buildPractitionerPhoneRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

            if (practitionerPhoneRecipient != null) {
                recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForDepartmentUsers(Long departmentId) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForPhysicianDepartmentUsers(Long departmentId) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildPhysicianDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("PHYSICIAN_DEPARTMENT_USERS", departmentUsers);
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForNurseDepartmentUsers(Long departmentId) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildNurseDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("NURSE_DEPARTMENT_USERS", departmentUsers);
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForPatient(Patient patient) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        if (patient != null) {
            NotificationResolvedRecipientDTO patientEmailRecipient =
                    buildPatientEmailRecipient(patient, organizationDefinitionDTO);

            if (patientEmailRecipient != null) {
                recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
            }

            NotificationResolvedRecipientDTO patientPhoneRecipient =
                    buildPatientPhoneRecipient(patient, organizationDefinitionDTO);

            if (patientPhoneRecipient != null) {
                recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
            }
            NotificationResolvedRecipientDTO patientPushRecipient =
                    buildPatientPushRecipient(patient, organizationDefinitionDTO);

            if (patientPushRecipient != null) {
                recipientsByRule.put("PATIENT_USER", List.of(patientPushRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForPractitioner(PractitionerDTO practitionerDTO) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        if (practitionerDTO != null) {
            UserDTO practitionerUser = null;
            if (practitionerDTO.userId() != null) {
                practitionerUser = userClient.getUserByUserId(practitionerDTO.userId());
            }
            NotificationResolvedRecipientDTO practitionerEmailRecipient =
                    buildPractitionerEmailRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

            if (practitionerEmailRecipient != null) {
                recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
            }

            NotificationResolvedRecipientDTO practitionerUserRecipient =
                    buildPractitionerUserRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

            if (practitionerUserRecipient != null) {
                recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
            }

            NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                    buildPractitionerPhoneRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);
            if (practitionerPhoneRecipient != null) {
                recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForCurrentUser(String login) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        NotificationResolvedRecipientDTO currentUser = buildCurrentUserRecipient(login, organizationDefinitionDTO);

        if (currentUser != null) {
            recipientsByRule.put("CURRENT_USER", List.of(currentUser));
        }

        NotificationResolvedRecipientDTO currentUserPhoneRecipient = buildCurrentUserPhoneRecipient(login, organizationDefinitionDTO);
        if (currentUserPhoneRecipient != null) {
            recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForCreatedByUser(String createdByLogin) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        NotificationResolvedRecipientDTO createdByUser = buildCreatedByUserRecipient(createdByLogin, organizationDefinitionDTO);

        if (createdByUser != null) {
            recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
        }

        NotificationResolvedRecipientDTO createdByUserPhoneRecipient = buildCreatedByUserPhoneRecipient(createdByLogin, organizationDefinitionDTO);
        if (createdByUserPhoneRecipient != null) {
            recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
        }


        return recipientsByRule;
    }

    public void addDepartmentUsers(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Long departmentId) {
        if (recipientsByRule == null || departmentId == null) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);


        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
        }
    }

    public void addPhysicianDepartmentUsers(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Long departmentId) {
        if (recipientsByRule == null || departmentId == null) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);


        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildPhysicianDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("PHYSICIAN_DEPARTMENT_USERS", departmentUsers);
        }
    }

    public void addNurseDepartmentUsers(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Long departmentId) {
        if (recipientsByRule == null || departmentId == null) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);


        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildNurseDepartmentUserRecipients(departmentId, organizationDefinitionDTO);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("NURSE_DEPARTMENT_USERS", departmentUsers);
        }
    }

    public void addCreatedByUser(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, String createdByLogin) {
        if (recipientsByRule == null || createdByLogin == null || createdByLogin.isBlank()) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        NotificationResolvedRecipientDTO createdByUser =
                buildCreatedByUserRecipient(createdByLogin, organizationDefinitionDTO);

        if (createdByUser != null) {
            recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
        }

        NotificationResolvedRecipientDTO createdByUserPhoneRecipient =
                buildCreatedByUserPhoneRecipient(createdByLogin, organizationDefinitionDTO);
        if (createdByUserPhoneRecipient != null) {
            recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
        }
    }

    public void addCurrentUser(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, String login) {
        if (recipientsByRule == null || login == null || login.isBlank()) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);

        NotificationResolvedRecipientDTO currentUser =
                buildCurrentUserRecipient(login, organizationDefinitionDTO);

        if (currentUser != null) {
            recipientsByRule.put("CURRENT_USER", List.of(currentUser));
        }

        NotificationResolvedRecipientDTO currentUserPhoneRecipient =
                buildCurrentUserPhoneRecipient(login, organizationDefinitionDTO);
        if (currentUserPhoneRecipient != null) {
            recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
        }
    }

    public void addPatient(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Patient patient) {
        if (recipientsByRule == null || patient == null) {
            return;
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);


        NotificationResolvedRecipientDTO patientEmailRecipient =
                buildPatientEmailRecipient(patient, organizationDefinitionDTO);

        if (patientEmailRecipient != null) {
            recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
        }

        NotificationResolvedRecipientDTO patientPhoneRecipient =
                buildPatientPhoneRecipient(patient, organizationDefinitionDTO);

        if (patientPhoneRecipient != null) {
            recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
        }

        NotificationResolvedRecipientDTO patientPushRecipient =
                buildPatientPushRecipient(
                        patient,
                        organizationDefinitionDTO
                );

        if (patientPushRecipient != null) {
            recipientsByRule.put(
                    "PATIENT_USER",
                    List.of(patientPushRecipient)
            );
        }
    }

    public void addPractitioner(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, PractitionerDTO practitionerDTO) {
        if (recipientsByRule == null || practitionerDTO == null) {
            return;
        }
        UserDTO practitionerUser = null;
        if (practitionerDTO.userId() != null) {
            practitionerUser = userClient.getUserByUserId(practitionerDTO.userId());
        }
        List<OrganizationDefinitionDTO> organizationDefinitionList = organizationClient.getOrganization();
        OrganizationDefinitionDTO organizationDefinitionDTO = organizationDefinitionList.stream().findFirst().orElse(null);


        NotificationResolvedRecipientDTO practitionerEmailRecipient =
                buildPractitionerEmailRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

        if (practitionerEmailRecipient != null) {
            recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
        }

        NotificationResolvedRecipientDTO practitionerUserRecipient =
                buildPractitionerUserRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

        if (practitionerUserRecipient != null) {
            recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
        }

        NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                buildPractitionerPhoneRecipient(practitionerDTO, practitionerUser, organizationDefinitionDTO);

        if (practitionerPhoneRecipient != null) {
            recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
        }
    }

    public List<NotificationResolvedRecipientDTO> buildDepartmentUserRecipients(Long departmentId, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users;
        DepartmentDTO department;

        try {
            department = departmentHelper.getDepartment(departmentId);
            users = userDepartmentHelper.getUsersForDepartment(departmentId);

        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to get department users. departmentId={}, error={}",
                    departmentId,
                    e.getMessage()
            );
            return List.of();
        }

        if (users == null || users.isEmpty()) {
            return List.of();
        }
        for (UserDTO user : users) {
            log.info(
                    "[NOTIFICATION] User id={}, login={}, email={}",
                    user.id(),
                    user.login(),
                    user.email()
            );
        }

        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of(
                                "departmentId", departmentId,
                                "departmentName", department.name()
                        ))
                        .toEmails(List.of(user.email()))
                        .language(user.langKey() != null && !user.langKey().isBlank() ? user.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                        .build()
                )
                .toList();
    }

    public List<NotificationResolvedRecipientDTO> buildPhysicianDepartmentUserRecipients(Long departmentId, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users;
        DepartmentDTO department;

        try {
            department = departmentHelper.getDepartment(departmentId);
            users = userDepartmentHelper.getPhysicianUsersForDepartment(departmentId);

        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to get physician department users. departmentId={}, error={}",
                    departmentId,
                    e.getMessage()
            );
            return List.of();
        }

        if (users == null || users.isEmpty()) {
            return List.of();
        }


        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of(
                                "departmentId", departmentId,
                                "departmentName", department.name()
                        ))
                        .language(user.langKey() != null && !user.langKey().isBlank() ? user.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                        .build()
                )
                .toList();
    }

    public List<NotificationResolvedRecipientDTO> buildNurseDepartmentUserRecipients(Long departmentId, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users;
        DepartmentDTO department;

        try {
            department = departmentHelper.getDepartment(departmentId);
            users = userDepartmentHelper.getNurseUsersForDepartment(departmentId);

        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to get physician department users. departmentId={}, error={}",
                    departmentId,
                    e.getMessage()
            );
            return List.of();
        }

        if (users == null || users.isEmpty()) {
            return List.of();
        }


        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of(
                                "departmentId", departmentId,
                                "departmentName", department.name()
                        ))
                        .language(user.langKey() != null && !user.langKey().isBlank() ? user.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                        .build()
                )
                .toList();
    }

    public List<NotificationResolvedRecipientDTO> buildDepartmentUserRecipientsForScheduledNotification(Long departmentId, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (departmentId == null) {
            return List.of();
        }

        List<UserDTO> users;
        DepartmentDTO department;

        try {
            department = departmentHelper.getDepartmentInternal(departmentId);
            users = userDepartmentHelper.getUsersForDepartmentInternal(departmentId);

        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to get department users. departmentId={}, error={}",
                    departmentId,
                    e.getMessage()
            );
            return List.of();
        }

        if (users == null || users.isEmpty()) {
            return List.of();
        }


        return users.stream()
                .filter(user -> user != null && user.id() != null)
                .map(user -> NotificationResolvedRecipientDTO.builder()
                        .recipientType("USER")
                        .recipientId(user.id())
                        .recipientName(getUserDisplayName(user))
                        .recipientEmail(user.email())
                        .recipientData(Map.of(
                                "departmentId", departmentId,
                                "departmentName", department.name()
                        ))
                        .language(user.langKey() != null && !user.langKey().isBlank() ? user.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                        .build()
                )
                .toList();
    }

    public NotificationResolvedRecipientDTO buildCreatedByUserRecipient(
            String createdByLogin,
            OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {

        if (createdByLogin == null || createdByLogin.isBlank()) {
            return null;
        }

        UserDTO user;

        try {
            user = userClient.getUserByLogin(createdByLogin);
        } catch (Exception e) {
            log.error(
                    "[NOTIFICATION] Failed to resolve CREATED_BY_USER. login={}",
                    createdByLogin,
                    e
            );
            return null;
        }

        if (user == null) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER because user is null. login={}",
                    createdByLogin
            );
            return null;
        }

        if (user.email() == null || user.email().isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER because email is missing. login={}",
                    createdByLogin
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(user.id())
                .recipientName(createdByLogin)
                .recipientEmail(user.email())
                .toEmails(List.of(user.email()))
                .recipientData(Map.of(
                        "createdByLogin", createdByLogin,
                        "createdByEmail", user.email(),
                        "createdByUserId", user.id()
                ))
                .language(
                        user.langKey() != null && !user.langKey().isBlank()
                                ? user.langKey()
                                : finalOrganizationDefinitionDTO != null
                                ? finalOrganizationDefinitionDTO.defaultLanguageName()
                                : "en"
                )
                .build();
    }

    public NotificationResolvedRecipientDTO buildCreatedByUserPhoneRecipient(
            String createdByLogin,
            OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {

        if (createdByLogin == null || createdByLogin.isBlank()) {
            return null;
        }

        UserDTO user;

        try {
            user = userClient.getUserByLogin(createdByLogin);
        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to resolve CREATED_BY_USER_PHONE. login={}",
                    createdByLogin
            );
            return null;
        }

        if (user == null) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER_PHONE because user is null. login={}",
                    createdByLogin
            );
            return null;
        }

        if (user.phoneNumber() == null || user.phoneNumber().isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER_PHONE because phone number is missing. login={}",
                    createdByLogin
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(user.id())
                .recipientName(createdByLogin)
                .recipientPhone(user.phoneNumber())
                .toPhone(user.phoneNumber())
                .recipientData(Map.of(
                        "createdByLogin", createdByLogin,
                        "createdByPhoneNumber", user.phoneNumber(),
                        "createdByUserId", user.id()
                ))
                .language(
                        user.langKey() != null && !user.langKey().isBlank()
                                ? user.langKey()
                                : finalOrganizationDefinitionDTO != null
                                ? finalOrganizationDefinitionDTO.defaultLanguageName()
                                : "en"
                )
                .build();
    }

    public NotificationResolvedRecipientDTO buildCurrentUserRecipient(
            String login,
            OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {

        if (login == null || login.isBlank()) {
            return null;
        }

        UserDTO user;

        try {
            user = userClient.getUserByLogin(login);
        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to resolve CURRENT_USER. login={}",
                    login
            );
            return null;
        }

        if (user == null || user.id() == null || user.email() == null || user.email().isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CURRENT_USER because user id and email are missing. login={}",
                    login
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(user.id())
                .recipientName(login)
                .recipientEmail(user.email())
                .toEmails(List.of(user.email()))
                .recipientData(Map.of(
                        "login", login,
                        "userId", user.id(),
                        "email", user.email()
                ))
                .language(
                        user.langKey() != null && !user.langKey().isBlank()
                                ? user.langKey()
                                : finalOrganizationDefinitionDTO != null
                                ? finalOrganizationDefinitionDTO.defaultLanguageName()
                                : "en"
                )
                .build();
    }

    public NotificationResolvedRecipientDTO buildCurrentUserPhoneRecipient(
            String login,
            OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {

        if (login == null || login.isBlank()) {
            return null;
        }

        UserDTO user;

        try {
            user = userClient.getUserByLogin(login);
        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to resolve CURRENT_USER_PHONE. login={}",
                    login
            );
            return null;
        }

        if (user == null
                || user.id() == null
                || user.phoneNumber() == null
                || user.phoneNumber().isBlank()) {

            log.warn(
                    "[NOTIFICATION] Skip CURRENT_USER_PHONE because user id or phone number is missing. login={}",
                    login
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(user.id())
                .recipientName(login)
                .recipientPhone(user.phoneNumber())
                .toPhone(user.phoneNumber())
                .recipientData(Map.of(
                        "login", login,
                        "userId", user.id(),
                        "phoneNumber", user.phoneNumber()
                ))
                .language(
                        user.langKey() != null && !user.langKey().isBlank()
                                ? user.langKey()
                                : finalOrganizationDefinitionDTO != null
                                ? finalOrganizationDefinitionDTO.defaultLanguageName()
                                : "en"
                )
                .build();
    }
    private NotificationResolvedRecipientDTO buildPatientPushRecipient(
            Patient patient,
            OrganizationDefinitionDTO finalOrganizationDefinitionDTO
    ) {
        if (patient == null || patient.getId() == null) {
            return null;
        }

        List<String> deviceTokens;

        try {
            deviceTokens = notificationClient.getActiveDeviceTokens(
                    "PATIENT",
                    patient.getId()
            );
        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to resolve patient push devices. patientId={}, error={}",
                    patient.getId(),
                    e.getMessage()
            );
            return null;
        }

        if (deviceTokens == null || deviceTokens.isEmpty()) {
            log.debug(
                    "[NOTIFICATION] No active push devices found for patientId={}",
                    patient.getId()
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PATIENT")
                .recipientId(patient.getId())
                .recipientName(getPatientName(patient))
                .deviceTokens(
                        deviceTokens.stream()
                                .filter(token -> token != null && !token.isBlank())
                                .distinct()
                                .toList()
                )
                .language(
                        patient.getPreferredLanguage() != null
                                && !patient.getPreferredLanguage().isBlank()
                                ? patient.getPreferredLanguage()
                                : finalOrganizationDefinitionDTO != null
                                ? finalOrganizationDefinitionDTO.defaultLanguageName()
                                : "en"
                )
                .build();
    }

    private NotificationResolvedRecipientDTO buildPatientEmailRecipient(Patient patient, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (patient == null) {
            return null;
        }

        if (patient.getEmail() == null || patient.getEmail().isBlank()) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PATIENT")
                .recipientId(patient.getId())
                .recipientName(getPatientName(patient))
                .recipientEmail(patient.getEmail())
                .toEmails(List.of(patient.getEmail()))
                .language(patient.getPreferredLanguage() != null && !patient.getPreferredLanguage().isBlank() ? patient.getPreferredLanguage() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                .build();
    }

    private NotificationResolvedRecipientDTO buildPatientPhoneRecipient(Patient patient, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (patient == null) {
            return null;
        }

        if (patient.getPrimaryMobileNumber() == null || patient.getPrimaryMobileNumber().isBlank()) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PATIENT")
                .recipientId(patient.getId())
                .recipientName(getPatientName(patient))
                .recipientPhone(patient.getPrimaryMobileNumber())
                .toPhone(patient.getPrimaryMobileNumber())
                .language(patient.getPreferredLanguage() != null && !patient.getPreferredLanguage().isBlank() ? patient.getPreferredLanguage() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                .build();
    }

    private NotificationResolvedRecipientDTO buildPractitionerEmailRecipient(PractitionerDTO practitionerDTO, UserDTO practitionerUser, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (practitionerDTO == null) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PRACTITIONER")
                .recipientId(practitionerDTO.id())
                .recipientName(practitionerDTO.firstName() + " " + practitionerDTO.lastName())
                .recipientEmail(practitionerDTO.email())
                .toEmails(List.of(practitionerDTO.email()))
                .language(practitionerUser != null && practitionerUser.langKey() != null && !practitionerUser.langKey().isBlank() ? practitionerUser.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                .build();
    }

    private NotificationResolvedRecipientDTO buildPractitionerPhoneRecipient(PractitionerDTO practitionerDTO, UserDTO practitionerUser, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (practitionerDTO == null) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PRACTITIONER")
                .recipientId(practitionerDTO.id())
                .recipientName(practitionerDTO.firstName() + " " + practitionerDTO.lastName())
                .recipientPhone(practitionerDTO.phoneNumber())
                .toPhone(practitionerDTO.phoneNumber())
                .language(practitionerUser != null && practitionerUser.langKey() != null && !practitionerUser.langKey().isBlank() ? practitionerUser.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                .build();
    }

    private NotificationResolvedRecipientDTO buildPractitionerUserRecipient(PractitionerDTO practitioner, UserDTO practitionerUser, OrganizationDefinitionDTO finalOrganizationDefinitionDTO) {
        if (practitioner == null || practitioner.userId() == null) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(practitioner.userId())
                .recipientName((safe(practitioner.firstName()) + " " + safe(practitioner.lastName())).trim())
                .recipientEmail(practitioner.email())
                .recipientData(Map.of(
                        "practitionerId", practitioner.id(),
                        "practitionerName", (safe(practitioner.firstName()) + " " + safe(practitioner.lastName())).trim(),
                        "practitionerEmail", practitioner.email(),
                        "practitionerUserId", practitioner.userId()
                ))
                .language(practitionerUser != null && practitionerUser.langKey() != null && !practitionerUser.langKey().isBlank() ? practitionerUser.langKey() : finalOrganizationDefinitionDTO != null ? finalOrganizationDefinitionDTO.defaultLanguageName() : "en")
                .build();
    }

    public String getPatientName(Patient patient) {
        if (patient == null) {
            return "";
        }

        String firstName = patient.getFirstName() != null ? patient.getFirstName() : "";
        String secondName = patient.getSecondName() != null ? patient.getSecondName() : "";
        String thirdName = patient.getThirdName() != null ? patient.getThirdName() : "";
        String lastName = patient.getLastName() != null ? patient.getLastName() : "";

        String fullName = (firstName + " " + secondName + " " + thirdName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
            return patient.getEmail();
        }

        return patient.getId() != null ? String.valueOf(patient.getId()) : "";
    }

    public String getUserDisplayName(UserDTO user) {
        if (user == null) {
            return "";
        }

        String firstName = user.firstName() != null ? user.firstName() : "";
        String lastName = user.lastName() != null ? user.lastName() : "";

        String fullName = (firstName + " " + lastName)
                .replaceAll("\\s+", " ")
                .trim();

        if (!fullName.isBlank()) {
            return fullName;
        }

        if (user.login() != null && !user.login().isBlank()) {
            return user.login();
        }

        if (user.email() != null && !user.email().isBlank()) {
            return user.email();
        }

        return user.id() != null ? String.valueOf(user.id()) : "";
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private Map<String, Map<String, List<NotificationResolvedRecipientDTO>>> groupRecipientsByLanguage(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule) {

        Map<String, Map<String, List<NotificationResolvedRecipientDTO>>> result = new LinkedHashMap<>();

        if (recipientsByRule == null) {
            return result;
        }

        for (Map.Entry<String, List<NotificationResolvedRecipientDTO>> ruleEntry : recipientsByRule.entrySet()) {

            String rule = ruleEntry.getKey();

            for (NotificationResolvedRecipientDTO recipient : ruleEntry.getValue()) {

                String language = recipient.getLanguage();

                if (language == null || language.isBlank()) {
                    language = "en";
                }

                result
                        .computeIfAbsent(language, k -> new LinkedHashMap<>())
                        .computeIfAbsent(rule, k -> new ArrayList<>())
                        .add(recipient);
            }
        }

        return result;
    }

    private Long getLoggedInFacility() {

        return SecurityUtils.getCurrentUserFacility()
                .orElse(null);

    }


}