package com.dazzle.asklepios.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationContextDTO {

    private Long patientId;

    private String patientName;

    private String patientEmail;

    private String patientPhone;

    private Long departmentId;

    private String departmentName;

    private Long practitionerId;

    private Long practitionerUserId;

    private String practitionerName;

    private String practitionerEmail;

    private String practitionerPhone;
}
