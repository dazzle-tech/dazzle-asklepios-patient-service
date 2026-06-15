package com.dazzle.asklepios.client.notification;

import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "asklepios-notification-service", url = "${service.asklepios-notification-service-url}")
public interface NotificationClient {

    @PostMapping("/api/notification/notifications")
    void createNotification(NotificationCreateDTO dto);
}