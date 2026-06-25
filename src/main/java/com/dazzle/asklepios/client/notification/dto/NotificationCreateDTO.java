package com.dazzle.asklepios.client.notification.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreateDTO {

    private Long facilityId;

    @NotBlank
    private String code;

    @NotBlank
    private String language;

    private NotificationContextDTO context;

    private Map<String, List<NotificationResolvedRecipientDTO>> recipientsByRule;

    private Map<String, Object> data;

    private String relatedEntityType;

    private Long relatedEntityId;
}