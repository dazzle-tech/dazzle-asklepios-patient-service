package com.dazzle.asklepios.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResolvedRecipientDTO {

    private String recipientType;

    private Long recipientId;

    private String recipientName;

    private String recipientEmail;

    private String recipientPhone;

    private List<String> toEmails;

    private List<String> ccEmails;

    private List<String> bccEmails;

    private String toPhone;

    private Map<String, Object> recipientData;

    private String language;

}
