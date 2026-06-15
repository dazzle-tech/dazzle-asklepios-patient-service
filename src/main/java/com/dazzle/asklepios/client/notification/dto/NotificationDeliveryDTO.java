package com.dazzle.asklepios.client.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDeliveryDTO {

    private List<String> toEmails;

    private List<String> ccEmails;

    private List<String> bccEmails;

    private String toPhone;
}
