package com.dazzle.asklepios.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRecipientDTO {

    private String recipientType;

    private Long recipientId;

    private String recipientName;

    private String recipientEmail;

    private String recipientPhone;

    private Map<String, Object> recipientData;
}