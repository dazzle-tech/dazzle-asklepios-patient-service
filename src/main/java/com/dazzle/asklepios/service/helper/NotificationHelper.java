package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.notification.NotificationClient;
import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import com.dazzle.asklepios.client.notification.dto.NotificationResolvedRecipientDTO;
import com.dazzle.asklepios.client.setup.UserClient;
import com.dazzle.asklepios.client.setup.dto.DepartmentDTO;
import com.dazzle.asklepios.client.setup.dto.PractitionerDTO;
import com.dazzle.asklepios.client.setup.dto.UserDTO;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.enumeration.notification.NotificationCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    public void sendNotification(Long facilityId, NotificationCode code, String language, Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Map<String, Object> data, String relatedEntityType, Long relatedEntityId) {
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

        NotificationCreateDTO dto = new NotificationCreateDTO(
                facilityId,
                code,
                language != null && !language.isBlank() ? language : "en",
                null,
                recipientsByRule,
                data != null ? data : new LinkedHashMap<>(),
                relatedEntityType,
                relatedEntityId
        );

        try {
            log.debug(
                    "[NOTIFICATION] Creating notification. code={}, relatedEntityType={}, relatedEntityId={}, recipientsByRule={}, data={}",
                    code,
                    relatedEntityType,
                    relatedEntityId,
                    recipientsByRule,
                    data
            );

            notificationClient.createNotification(dto);
        } catch (Exception e) {
            log.warn(
                    "[NOTIFICATION] Failed to create notification. code={}, relatedEntityType={}, relatedEntityId={}, error={}",
                    code,
                    relatedEntityType,
                    relatedEntityId,
                    e.getMessage()
            );
        }
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> resolveRecipients(Long departmentId, String login, String createdByLogin, Patient patient, PractitionerDTO practitionerDTO) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        // Department users
        if (departmentId != null) {
            List<NotificationResolvedRecipientDTO> departmentUsers =
                    buildDepartmentUserRecipients(departmentId);

            if (!departmentUsers.isEmpty()) {
                recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
            }
        }

        // Current user
        if (login != null && !login.isBlank()) {
            NotificationResolvedRecipientDTO currentUser =
                    buildCurrentUserRecipient(login);

            if (currentUser != null) {
                recipientsByRule.put("CURRENT_USER", List.of(currentUser));
            }

            NotificationResolvedRecipientDTO currentUserPhoneRecipient =
                    buildCurrentUserPhoneRecipient(login);

            if (currentUserPhoneRecipient != null) {
                recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
            }
        }

        // Created by user
        if (createdByLogin != null && !createdByLogin.isBlank()) {
            NotificationResolvedRecipientDTO createdByUser =
                    buildCreatedByUserRecipient(createdByLogin);

            if (createdByUser != null) {
                recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
            }

            NotificationResolvedRecipientDTO createdByUserPhoneRecipient =
                    buildCreatedByUserPhoneRecipient(createdByLogin);

            if (createdByUserPhoneRecipient != null) {
                recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
            }
        }

        // Patient
        if (patient != null) {
            NotificationResolvedRecipientDTO patientEmailRecipient =
                    buildPatientEmailRecipient(patient);

            if (patientEmailRecipient != null) {
                recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
            }

            NotificationResolvedRecipientDTO patientPhoneRecipient =
                    buildPatientPhoneRecipient(patient);

            if (patientPhoneRecipient != null) {
                recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
            }
        }

        // Practitioner
        if (practitionerDTO != null) {
            NotificationResolvedRecipientDTO practitionerEmailRecipient =
                    buildPractitionerEmailRecipient(practitionerDTO);

            if (practitionerEmailRecipient != null) {
                recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
            }

            NotificationResolvedRecipientDTO practitionerUserRecipient =
                    buildPractitionerUserRecipient(practitionerDTO);

            if (practitionerUserRecipient != null) {
                recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
            }
            NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                    buildPractitionerPhoneRecipient(practitionerDTO);

            if (practitionerPhoneRecipient != null) {
                recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForDepartmentUsers(Long departmentId) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildDepartmentUserRecipients(departmentId);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForPatient(Patient patient) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        if (patient != null) {
            NotificationResolvedRecipientDTO patientEmailRecipient =
                    buildPatientEmailRecipient(patient);

            if (patientEmailRecipient != null) {
                recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
            }

            NotificationResolvedRecipientDTO patientPhoneRecipient =
                    buildPatientPhoneRecipient(patient);

            if (patientPhoneRecipient != null) {
                recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForPractitioner(PractitionerDTO practitionerDTO) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        if (practitionerDTO != null) {
            NotificationResolvedRecipientDTO practitionerEmailRecipient =
                    buildPractitionerEmailRecipient(practitionerDTO);

            if (practitionerEmailRecipient != null) {
                recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
            }

            NotificationResolvedRecipientDTO practitionerUserRecipient =
                    buildPractitionerUserRecipient(practitionerDTO);

            if (practitionerUserRecipient != null) {
                recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
            }

            NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                    buildPractitionerPhoneRecipient(practitionerDTO);
            if (practitionerPhoneRecipient != null) {
                recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
            }
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForCurrentUser(String login) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        NotificationResolvedRecipientDTO currentUser = buildCurrentUserRecipient(login);

        if (currentUser != null) {
            recipientsByRule.put("CURRENT_USER", List.of(currentUser));
        }

        NotificationResolvedRecipientDTO currentUserPhoneRecipient = buildCurrentUserPhoneRecipient(login);
        if (currentUserPhoneRecipient != null) {
            recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
        }

        return recipientsByRule;
    }

    public Map<String, List<NotificationResolvedRecipientDTO>> recipientsForCreatedByUser(String createdByLogin) {
        Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule = new LinkedHashMap<>();

        NotificationResolvedRecipientDTO createdByUser = buildCreatedByUserRecipient(createdByLogin);

        if (createdByUser != null) {
            recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
        }

        NotificationResolvedRecipientDTO createdByUserPhoneRecipient = buildCreatedByUserPhoneRecipient(createdByLogin);
        if (createdByUserPhoneRecipient != null) {
            recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
        }


        return recipientsByRule;
    }

    public void addDepartmentUsers(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Long departmentId) {
        if (recipientsByRule == null || departmentId == null) {
            return;
        }

        List<NotificationResolvedRecipientDTO> departmentUsers =
                buildDepartmentUserRecipients(departmentId);

        if (!departmentUsers.isEmpty()) {
            recipientsByRule.put("DEPARTMENT_USERS", departmentUsers);
        }
    }

    public void addCreatedByUser(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, String createdByLogin) {
        if (recipientsByRule == null || createdByLogin == null || createdByLogin.isBlank()) {
            return;
        }

        NotificationResolvedRecipientDTO createdByUser =
                buildCreatedByUserRecipient(createdByLogin);

        if (createdByUser != null) {
            recipientsByRule.put("CREATED_BY_USER", List.of(createdByUser));
        }

        NotificationResolvedRecipientDTO createdByUserPhoneRecipient =
                buildCreatedByUserPhoneRecipient(createdByLogin);
        if (createdByUserPhoneRecipient != null) {
            recipientsByRule.put("CREATED_BY_USER_PHONE", List.of(createdByUserPhoneRecipient));
        }
    }

    public void addCurrentUser(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, String login) {
        if (recipientsByRule == null || login == null || login.isBlank()) {
            return;
        }

        NotificationResolvedRecipientDTO currentUser =
                buildCurrentUserRecipient(login);

        if (currentUser != null) {
            recipientsByRule.put("CURRENT_USER", List.of(currentUser));
        }

        NotificationResolvedRecipientDTO currentUserPhoneRecipient =
                buildCurrentUserPhoneRecipient(login);
        if (currentUserPhoneRecipient != null) {
            recipientsByRule.put("CURRENT_USER_PHONE", List.of(currentUserPhoneRecipient));
        }
    }

    public void addPatient(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, Patient patient) {
        if (recipientsByRule == null || patient == null) {
            return;
        }

        NotificationResolvedRecipientDTO patientEmailRecipient =
                buildPatientEmailRecipient(patient);

        if (patientEmailRecipient != null) {
            recipientsByRule.put("PATIENT_EMAIL", List.of(patientEmailRecipient));
        }

        NotificationResolvedRecipientDTO patientPhoneRecipient =
                buildPatientPhoneRecipient(patient);

        if (patientPhoneRecipient != null) {
            recipientsByRule.put("PATIENT_PHONE", List.of(patientPhoneRecipient));
        }
    }

    public void addPractitioner(Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule, PractitionerDTO practitionerDTO) {
        if (recipientsByRule == null || practitionerDTO == null) {
            return;
        }

        NotificationResolvedRecipientDTO practitionerEmailRecipient =
                buildPractitionerEmailRecipient(practitionerDTO);

        if (practitionerEmailRecipient != null) {
            recipientsByRule.put("PRACTITIONER_EMAIL", List.of(practitionerEmailRecipient));
        }

        NotificationResolvedRecipientDTO practitionerUserRecipient =
                buildPractitionerUserRecipient(practitionerDTO);

        if (practitionerUserRecipient != null) {
            recipientsByRule.put("PRACTITIONER_USER", List.of(practitionerUserRecipient));
        }

        NotificationResolvedRecipientDTO practitionerPhoneRecipient =
                buildPractitionerPhoneRecipient(practitionerDTO);

        if (practitionerPhoneRecipient != null) {
            recipientsByRule.put("PRACTITIONER_PHONE", List.of(practitionerPhoneRecipient));
        }
    }

    public List<NotificationResolvedRecipientDTO> buildDepartmentUserRecipients(Long departmentId) {
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
                        .build()
                )
                .toList();
    }

    public NotificationResolvedRecipientDTO buildCreatedByUserRecipient(String createdByLogin) {
        if (createdByLogin == null || createdByLogin.isBlank()) {
            return null;
        }

        Long userId = userClient.getUser(createdByLogin).id();
        String email = userClient.getUser(createdByLogin).email();

        if (email == null || email.isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER because email is missing. login={}",
                    createdByLogin
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(userId)
                .recipientName(createdByLogin)
                .recipientEmail(email)
                .toEmails(List.of(email))
                .recipientData(Map.of(
                        "createdByLogin", createdByLogin,
                        "createdByEmail", email,
                        "createdByUserId", userId

                ))
                .build();
    }

    public NotificationResolvedRecipientDTO buildCreatedByUserPhoneRecipient(String createdByLogin) {
        if (createdByLogin == null || createdByLogin.isBlank()) {
            return null;
        }

        Long userId = userClient.getUser(createdByLogin).id();
        String phoneNumber = userClient.getUser(createdByLogin).phoneNumber();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CREATED_BY_USER_PHONE because phone number is missing. login={}",
                    createdByLogin
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(userId)
                .recipientName(createdByLogin)
                .recipientPhone(phoneNumber)
                .toPhone(phoneNumber)
                .recipientData(Map.of(
                        "createdByLogin", createdByLogin,
                        "createdByPhoneNumber", phoneNumber,
                        "createdByUserId", userId
                ))
                .build();
    }

    public NotificationResolvedRecipientDTO buildCurrentUserRecipient(String login) {
        if (login == null || login.isBlank()) {
            return null;
        }

        Long userId = userClient.getUser(login).id();
        String email = userClient.getUser(login).email();

        if (userId == null && (email == null || email.isBlank())) {
            log.warn(
                    "[NOTIFICATION] Skip CURRENT_USER because user id and email are missing. login={}",
                    login
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(userId)
                .recipientName(login)
                .recipientEmail(email)
                .toEmails(email != null && !email.isBlank() ? List.of(email) : List.of())
                .recipientData(Map.of(
                        "login", login,
                        "userId", userId,
                        "email", email
                ))
                .build();
    }

    public NotificationResolvedRecipientDTO buildCurrentUserPhoneRecipient(String login) {
        if (login == null || login.isBlank()) {
            return null;
        }

        Long userId = userClient.getUser(login).id();
        String phoneNumber = userClient.getUser(login).phoneNumber();

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn(
                    "[NOTIFICATION] Skip CURRENT_USER_PHONE because phone number is missing. login={}",
                    login
            );
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("USER")
                .recipientId(userId)
                .recipientName(login)
                .recipientPhone(phoneNumber)
                .toPhone(phoneNumber)
                .recipientData(Map.of(
                        "login", login,
                        "userId", userId,
                        "phoneNumber", phoneNumber
                ))
                .build();
    }

    private NotificationResolvedRecipientDTO buildPatientEmailRecipient(Patient patient) {
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
                .build();
    }

    private NotificationResolvedRecipientDTO buildPatientPhoneRecipient(Patient patient) {
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

    private NotificationResolvedRecipientDTO buildPractitionerEmailRecipient(PractitionerDTO practitionerDTO) {
        if (practitionerDTO == null) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PRACTITIONER")
                .recipientId(practitionerDTO.id())
                .recipientName(practitionerDTO.firstName() + " " + practitionerDTO.lastName())
                .recipientEmail(practitionerDTO.email())
                .toEmails(List.of(practitionerDTO.email()))
                .build();
    }

    private NotificationResolvedRecipientDTO buildPractitionerPhoneRecipient(PractitionerDTO practitionerDTO) {
        if (practitionerDTO == null) {
            return null;
        }

        return NotificationResolvedRecipientDTO.builder()
                .recipientType("PRACTITIONER")
                .recipientId(practitionerDTO.id())
                .recipientName(practitionerDTO.firstName() + " " + practitionerDTO.lastName())
                .recipientPhone(practitionerDTO.phoneNumber())
                .toPhone(practitionerDTO.phoneNumber())
                .build();
    }

    private NotificationResolvedRecipientDTO buildPractitionerUserRecipient(PractitionerDTO practitioner) {
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
                .build();
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

}
