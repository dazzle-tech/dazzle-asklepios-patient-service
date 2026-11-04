package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterDashboardPreference;
import com.dazzle.asklepios.domain.EncounterDashboardPreferenceWidget;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.domain.enumeration.EncounterDashboardWidgetKey;
import com.dazzle.asklepios.repository.EncounterDashboardPreferenceRepository;
import com.dazzle.asklepios.repository.EncounterDashboardPreferenceWidgetRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientDashboard.EncounterDashboardPreferenceVM;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EncounterDashboardPreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterDashboardPreferenceService.class);
    private static final String ENTITY = "encounterDashboardPreference";

    private final EncounterDashboardPreferenceRepository preferenceRepository;
    private final EncounterDashboardPreferenceWidgetRepository widgetRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final UserRepository userRepository;

    public EncounterDashboardPreferenceService(
        EncounterDashboardPreferenceRepository preferenceRepository,
        EncounterDashboardPreferenceWidgetRepository widgetRepository,
        PatientEncounterRepository patientEncounterRepository,
        UserRepository userRepository
    ) {
        this.preferenceRepository = preferenceRepository;
        this.widgetRepository = widgetRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public EncounterDashboardPreferenceVM get(Long encounterId) {
        User user = requireCurrentUser();
        assertEncounterExists(encounterId);

        return preferenceRepository
            .findByUserIdAndEncounterId(user.getId(), encounterId)
            .map(preference -> toVm(user.getId(), encounterId, true, keysOf(preference)))
            .orElseGet(() -> toVm(user.getId(), encounterId, false, List.of()));
    }

    public EncounterDashboardPreferenceVM save(Long encounterId, List<String> requestedKeys) {
        User user = requireCurrentUser();
        assertEncounterExists(encounterId);
        List<String> keys = normalize(requestedKeys);

        EncounterDashboardPreference preference = upsertPreference(user.getId(), encounterId);
        replaceWidgets(preference, keys);

        LOG.debug(
            "Saved encounter dashboard preference userId={} encounterId={} widgets={}",
            user.getId(),
            encounterId,
            keys
        );
        return toVm(user.getId(), encounterId, true, keys);
    }

    private EncounterDashboardPreference upsertPreference(Long userId, Long encounterId) {
        EncounterDashboardPreference existing = preferenceRepository
            .findByUserIdAndEncounterIdForUpdate(userId, encounterId)
            .orElse(null);

        if (existing != null) {
            existing.setLastModifiedDate(Instant.now());
            return preferenceRepository.saveAndFlush(existing);
        }

        EncounterDashboardPreference created = EncounterDashboardPreference.builder()
            .userId(userId)
            .encounterId(encounterId)
            .build();

        try {
            return preferenceRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException | JpaSystemException conflict) {
            EncounterDashboardPreference recovered = preferenceRepository
                .findByUserIdAndEncounterIdForUpdate(userId, encounterId)
                .orElseThrow(() ->
                    new IllegalStateException(
                        "Preference row is missing after conflict for userId=" +
                        userId +
                        " encounterId=" +
                        encounterId,
                        conflict
                    )
                );
            recovered.setLastModifiedDate(Instant.now());
            return preferenceRepository.saveAndFlush(recovered);
        }
    }

    private void replaceWidgets(EncounterDashboardPreference preference, List<String> keys) {
        widgetRepository.deleteByPreference_Id(preference.getId());
        widgetRepository.flush();

        if (keys.isEmpty()) {
            return;
        }

        List<EncounterDashboardPreferenceWidget> widgets = keys
            .stream()
            .map(key ->
                EncounterDashboardPreferenceWidget.builder().preference(preference).widgetKey(key).build()
            )
            .toList();
        widgetRepository.saveAllAndFlush(widgets);
    }

    private List<String> normalize(List<String> requestedKeys) {
        if (requestedKeys == null) {
            throw new BadRequestAlertException("widgetKeys is required", ENTITY, "widgetKeys.required");
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String key : requestedKeys) {
            if (key == null) {
                throw new BadRequestAlertException(
                    "Unknown dashboard widget: " + key,
                    ENTITY,
                    "widget.invalid"
                );
            }
            try {
                EncounterDashboardWidgetKey.valueOf(key);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestAlertException(
                    "Unknown dashboard widget: " + key,
                    ENTITY,
                    "widget.invalid"
                );
            }
            selected.add(key);
        }
        return Arrays.stream(EncounterDashboardWidgetKey.values())
            .map(Enum::name)
            .filter(selected::contains)
            .toList();
    }

    private List<String> keysOf(EncounterDashboardPreference preference) {
        Set<String> selected = new LinkedHashSet<>();
        if (preference.getWidgets() != null) {
            for (EncounterDashboardPreferenceWidget widget : preference.getWidgets()) {
                if (widget.getWidgetKey() != null) {
                    selected.add(widget.getWidgetKey());
                }
            }
        }
        return Arrays.stream(EncounterDashboardWidgetKey.values())
            .map(Enum::name)
            .filter(selected::contains)
            .toList();
    }

    private void assertEncounterExists(Long encounterId) {
        if (encounterId == null || !patientEncounterRepository.existsById(encounterId)) {
            throw new NotFoundAlertException(
                "Encounter not found with id " + encounterId,
                ENTITY,
                "encounter.notfound"
            );
        }
    }

    private User requireCurrentUser() {
        String login = SecurityUtils.getCurrentUserLogin()
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));
        return userRepository
            .findByLogin(login)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated."));
    }

    private EncounterDashboardPreferenceVM toVm(Long userId, Long encounterId, boolean customized, List<String> keys) {
        return new EncounterDashboardPreferenceVM(userId, encounterId, customized, keys);
    }
}
