package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PreAuthorizationAlertClose;
import com.dazzle.asklepios.repository.PreAuthorizationAlertCloseRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.PreAuthorizationAlertCloseRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class PreAuthorizationAlertCloseService {

    private final PreAuthorizationAlertCloseRepository repository;

    public PreAuthorizationAlertCloseService(PreAuthorizationAlertCloseRepository repository) {
        this.repository = repository;
    }

    public List<PreAuthorizationAlertClose> record(PreAuthorizationAlertCloseRequest request) {
        String closedBy = SecurityUtils.getCurrentUserLogin().orElse("system");
        Instant closedAt = Instant.now();
        String message = request.message().trim();
        List<PreAuthorizationAlertCloseRequest.Item> items = request.items() == null || request.items().isEmpty()
                ? List.of(new PreAuthorizationAlertCloseRequest.Item(null, null))
                : request.items();
        List<PreAuthorizationAlertClose> saved = new ArrayList<>();
        for (PreAuthorizationAlertCloseRequest.Item item : items) {
            String label = item == null || item.label() == null || item.label().isBlank()
                    ? null
                    : item.label().trim();
            saved.add(repository.save(PreAuthorizationAlertClose.builder()
                    .encounterId(request.encounterId())
                    .patientServiceProductId(item == null ? null : item.patientServiceProductId())
                    .itemLabel(label == null ? null : trim(label, 500))
                    .message(trim(message, 1000))
                    .closedBy(trim(closedBy, 50))
                    .closedAt(closedAt)
                    .build()));
        }
        return saved;
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
