package com.dazzle.asklepios.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCreateDTO {

    private Long facilityId;

    private String code;

    private String channel;

    private String language;

    private NotificationRecipientDTO recipient;

    private NotificationDeliveryDTO delivery;

    private Map<String, Object> data;

    private String relatedEntityType;

    private Long relatedEntityId;
}
