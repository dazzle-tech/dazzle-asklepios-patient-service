package com.dazzle.asklepios.client.notification.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NotificationCreatedResponse {

    private Long id;

    private String channel;

    private String recipientType;

    private Long recipientId;
}
