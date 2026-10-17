package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterDashboardPreference;
import com.dazzle.asklepios.domain.EncounterDashboardPreferenceWidget;
import com.dazzle.asklepios.domain.User;
import com.dazzle.asklepios.repository.EncounterDashboardPreferenceRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.UserRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import com.dazzle.asklepios.web.rest.vm.patientDashboard.EncounterDashboardPreferenceVM;
import jakarta.persistence.EntityManager;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class EncounterDashboardPreferenceService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterDashboardPreferenceService.class);
    private static final String ENTITY = "encounterDashboardPreference";

    /**
     * Canonical widget order. Stored selections are returned in this order.
     */
    static final List<String> WIDGET_KEYS = List.of(
        "c2",
        "c3",
        "c4",
        "c5",
        "c7",
        "c8",
        "c9",
        "c11",
        "c12",
        "c13",
        "c14"
    );

    private static final Set<String> ALLOWED_KEYS = Set.copyOf(WIDGET_KEYS);

    private final EncounterDashboardPreferenceRepository preferenceRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public EncounterDashboardPreferenceService(
        EncounterDashboardPreferenceRepository preferenceRepository,
        PatientEncounterRepository patientEncounterRepository,
        UserRepository userRepository,
        EntityManager entityManager
    ) {
        this.preferenceRepository = preferenceRepository;
        this.patientEncounterRepository = patientEncounterRepository;
        this.userRepository = userRepository;
        this.entityManager = entityManager;
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

        Long preferenceId = upsertPreference(user.getId(), encounterId);
        replaceWidgets(preferenceId, keys);

        LOG.debug(
            "Saved encounter dashboard preference userId={} encounterId={} widgets={}",
            user.getId(),
            encounterId,
            keys
        );
        return toVm(user.getId(), encounterId, true, keys);
    }

    private Long upsertPreference(Long userId, Long encounterId) {
        String actor = currentActor();
        entityManager
            .createNativeQuery(
                """
                INSERT INTO encounter_dashboard_preference
                    (user_id, encounter_id, created_by, created_date, last_modified_by, last_modified_date)
                VALUES
                    (:userId, :encounterId, :actor, CURRENT_TIMESTAMP, :actor, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, encounter_id)
                DO UPDATE SET
                    last_modified_by = EXCLUDED.last_modified_by,
                    last_modified_date = CURRENT_TIMESTAMP
                """
            )
            .setParameter("userId", userId)
            .setParameter("encounterId", encounterId)
            .setParameter("actor", actor)
            .executeUpdate();

        Number id = (Number) entityManager
            .createNativeQuery(
                """
                SELECT id FROM encounter_dashboard_preference
                WHERE user_id = :userId AND encounter_id = :encounterId
                """
            )
            .setParameter("userId", userId)
            .setParameter("encounterId", encounterId)
            .getSingleResult();
        return id.longValue();
    }

    private void replaceWidgets(Long preferenceId, List<String> keys) {
        entityManager
            .createNativeQuery("DELETE FROM encounter_dashboard_preference_widget WHERE preference_id = :preferenceId")
            .setParameter("preferenceId", preferenceId)
            .executeUpdate();
        entityManager.flush();

        for (String key : keys) {
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO encounter_dashboard_preference_widget (preference_id, widget_key)
                    VALUES (:preferenceId, :widgetKey)
                    """
                )
                .setParameter("preferenceId", preferenceId)
                .setParameter("widgetKey", key)
                .executeUpdate();
        }
    }

    private List<String> normalize(List<String> requestedKeys) {
        if (requestedKeys == null) {
            throw new BadRequestAlertException("widgetKeys is required", ENTITY, "widgetKeys.required");
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (String key : requestedKeys) {
            if (key == null || !ALLOWED_KEYS.contains(key)) {
                throw new BadRequestAlertException(
                    "Unknown dashboard widget: " + key,
                    ENTITY,
                    "widget.invalid"
                );
            }
            selected.add(key);
        }
        return WIDGET_KEYS.stream().filter(selected::contains).toList();
    }

    private List<String> keysOf(EncounterDashboardPreference preference) {
        Set<String> selected = new LinkedHashSet<>();
        if (preference.getWidgets() != null) {
            for (EncounterDashboardPreferenceWidget widget : preference.getWidgets()) {
                if (widget.getWidgetKey() != null && ALLOWED_KEYS.contains(widget.getWidgetKey())) {
                    selected.add(widget.getWidgetKey());
                }
            }
        }
        return WIDGET_KEYS.stream().filter(selected::contains).toList();
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

    private String currentActor() {
        String login = SecurityUtils.getCurrentUserLogin().orElse("system");
        return login.length() > 50 ? login.substring(0, 50) : login;
    }

    private EncounterDashboardPreferenceVM toVm(Long userId, Long encounterId, boolean customized, List<String> keys) {
        return new EncounterDashboardPreferenceVM(userId, encounterId, customized, keys);
    }
}
