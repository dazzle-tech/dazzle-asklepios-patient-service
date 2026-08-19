package com.dazzle.asklepios.client.notification;

import com.dazzle.asklepios.client.notification.dto.NotificationCreateDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "asklepios-notification-service", url = "${service.asklepios-notification-service-url}")
public interface NotificationClient {

    @PostMapping("/api/notification/notifications")
    void createNotification(NotificationCreateDTO dto);

    @GetMapping("/api/notification/devices/active-tokens")
    List<String> getActiveDeviceTokens(@RequestParam("recipientType") String recipientType, @RequestParam("recipientId") Long recipientId);
}