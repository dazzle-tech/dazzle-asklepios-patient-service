package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.EncounterAmendmentChangeLog;
import com.dazzle.asklepios.domain.enumeration.AmendmentHistoryAction;
import com.dazzle.asklepios.domain.enumeration.AmendmentMedicalSheet;
import com.dazzle.asklepios.repository.EncounterAmendmentChangeLogRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Writes one encounter_amendment_change_log row for an OPEN Admin Amendment.
 * A normal visit and old Reopen have no open session, so this returns without a row.
 * The caller and this save share the current transaction.
 */
@Service
public class EncounterAmendmentAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(EncounterAmendmentAuditService.class);

    private static final Set<String> TECHNICAL_FIELDS = Set.of(
            "id",
            "patient",
            "patientId",
            "encounter",
            "encounterId",
            "createdBy",
            "createdDate",
            "lastModifiedBy",
            "lastModifiedDate",
            "version",
            "reopenSessionId",
            "prescriptionNum",
            "consultationNumber"
    );

    private final EncounterReopenGuard encounterReopenGuard;
    private final EncounterAmendmentChangeLogRepository changeLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EncounterAmendmentAuditService(
            EncounterReopenGuard encounterReopenGuard,
            EncounterAmendmentChangeLogRepository changeLogRepository
    ) {
        this.encounterReopenGuard = encounterReopenGuard;
        this.changeLogRepository = changeLogRepository;
    }

    public Map<String, Object> capture(Object source) {
        return valuesOf(source, false);
    }

    public Map<String, Object> captureFields(Object source, String... fieldNames) {
        Map<String, Object> all = valuesOf(source, false);
        if (all == null) {
            return Map.of();
        }
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String name : fieldNames) {
            if (all.containsKey(name)) {
                selected.put(name, all.get(name));
            }
        }
        return selected;
    }

    /**
     * Stable allergy active-ingredient value. The child row stores only activeIngredientId.
     * Order is not clinical, so the list is sorted before comparison.
     */
    public static List<Map<String, Object>> activeIngredients(Collection<Long> activeIngredientIds) {
        if (activeIngredientIds == null || activeIngredientIds.isEmpty()) {
            return List.of();
        }
        return activeIngredientIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(id -> {
                    Map<String, Object> ingredient = new LinkedHashMap<>();
                    ingredient.put("activeIngredientId", id);
                    return ingredient;
                })
                .toList();
    }

    @Transactional
    public void added(Long encounterId, AmendmentMedicalSheet sheet, Long recordId, Object after) {
        write(encounterId, sheet, AmendmentHistoryAction.ADDED, recordId, null, after);
    }

    @Transactional
    public void changed(Long encounterId, AmendmentMedicalSheet sheet, Long recordId, Object before, Object after) {
        write(encounterId, sheet, AmendmentHistoryAction.CHANGED, recordId, before, after);
    }

    /**
     * A replacement save retires the previous row by flipping isActive.
     * The clinical values are unchanged, so a field diff would keep only isActive
     * and the history could not recover the previous pain level, height, or hearing result.
     * Both sides keep the clinical snapshot captured before that flip.
     */
    @Transactional
    public void deactivated(Long encounterId, AmendmentMedicalSheet sheet, Long recordId, Object beforeSource) {
        if (encounterId == null || sheet == null) {
            return;
        }
        Map<String, Object> before = meaningful(valuesOf(beforeSource, false));
        before = before == null ? new LinkedHashMap<>() : new LinkedHashMap<>(before);
        before.put("isActive", Boolean.TRUE);
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("isActive", Boolean.FALSE);
        persist(encounterId, sheet, AmendmentHistoryAction.CHANGED, recordId, before, after);
    }

    @Transactional
    public void removed(Long encounterId, AmendmentMedicalSheet sheet, Long recordId, Object before) {
        write(encounterId, sheet, AmendmentHistoryAction.REMOVED, recordId, before, null);
    }

    @Transactional
    public void cancelled(Long encounterId, AmendmentMedicalSheet sheet, Long recordId, Object before, Object after) {
        write(encounterId, sheet, AmendmentHistoryAction.CANCELLED, recordId, before, after);
    }

    private void write(
            Long encounterId,
            AmendmentMedicalSheet sheet,
            AmendmentHistoryAction action,
            Long recordId,
            Object beforeSource,
            Object afterSource
    ) {
        if (encounterId == null || sheet == null || action == null) {
            return;
        }

        Map<String, Object> before = action == AmendmentHistoryAction.ADDED
                ? null
                : meaningful(valuesOf(beforeSource, false));
        Map<String, Object> after = action == AmendmentHistoryAction.REMOVED
                ? null
                : meaningful(valuesOf(afterSource, false));

        if (action == AmendmentHistoryAction.CHANGED || action == AmendmentHistoryAction.CANCELLED) {
            Map<String, Object> changedBefore = new LinkedHashMap<>();
            Map<String, Object> changedAfter = new LinkedHashMap<>();
            retainChanges(before, after, changedBefore, changedAfter);
            if (changedBefore.isEmpty() && changedAfter.isEmpty()) {
                return;
            }
            before = changedBefore;
            after = changedAfter;
        } else if (action == AmendmentHistoryAction.ADDED && (after == null || after.isEmpty())) {
            return;
        } else if (action == AmendmentHistoryAction.REMOVED && (before == null || before.isEmpty())) {
            return;
        }

        persist(encounterId, sheet, action, recordId, before, after);
    }

    private void persist(
            Long encounterId,
            AmendmentMedicalSheet sheet,
            AmendmentHistoryAction action,
            Long recordId,
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        Long sessionId = encounterReopenGuard.findOpenReopenSessionId(encounterId).orElse(null);
        if (sessionId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("medicalSheet", sheet.name());
        payload.put("action", action.name());
        payload.put("recordId", recordId);
        payload.put("before", before);
        payload.put("after", after);

        EncounterAmendmentChangeLog row = new EncounterAmendmentChangeLog();
        row.setReopenSessionId(sessionId);
        row.setEncounterId(encounterId);
        row.setChangeData(json(payload));
        row.setChangedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        row.setChangedAt(Instant.now());
        changeLogRepository.save(row);
    }

    private static void retainChanges(
            Map<String, Object> before,
            Map<String, Object> after,
            Map<String, Object> changedBefore,
            Map<String, Object> changedAfter
    ) {
        Map<String, Object> left = before == null ? Map.of() : before;
        Map<String, Object> right = after == null ? Map.of() : after;
        for (String key : left.keySet()) {
            if (!Objects.equals(left.get(key), right.get(key))) {
                changedBefore.put(key, left.get(key));
                changedAfter.put(key, right.get(key));
            }
        }
        for (String key : right.keySet()) {
            if (!left.containsKey(key) && !Objects.equals(null, right.get(key))) {
                changedBefore.put(key, null);
                changedAfter.put(key, right.get(key));
            }
        }
    }

    private Map<String, Object> meaningful(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Map<String, Object> kept = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (value == null) {
                return;
            }
            if (value instanceof String text && text.isBlank()) {
                return;
            }
            kept.put(key, value);
        });
        return kept.isEmpty() ? null : kept;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> valuesOf(Object source, boolean unused) {
        if (source == null) {
            return null;
        }
        if (source instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null && !TECHNICAL_FIELDS.contains(String.valueOf(key))) {
                    copy.put(String.valueOf(key), normalize(value));
                }
            });
            return copy;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        Class<?> type = source.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                if (TECHNICAL_FIELDS.contains(field.getName()) || !isSimple(field.getType())) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    values.putIfAbsent(field.getName(), normalize(field.get(source)));
                } catch (IllegalAccessException ex) {
                    LOG.warn("Amendment audit skipped field {}", field.getName());
                }
            }
            type = type.getSuperclass();
        }
        return values;
    }

    private static boolean isSimple(Class<?> type) {
        return type.isPrimitive()
                || type.isEnum()
                || CharSequence.class.isAssignableFrom(type)
                || Number.class.isAssignableFrom(type)
                || Boolean.class.isAssignableFrom(type)
                || Temporal.class.isAssignableFrom(type)
                || type == Character.class;
    }

    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            return plainCollection(collection);
        }
        if (value instanceof Map<?, ?> map) {
            return plainMap(map);
        }
        if (value instanceof Enum<?> enumeration) {
            return enumeration.name();
        }
        if (value instanceof BigDecimal decimal) {
            BigDecimal stripped = decimal.stripTrailingZeros();
            if (stripped.scale() < 0) {
                stripped = stripped.setScale(0);
            }
            return stripped;
        }
        if (value instanceof Temporal temporal) {
            return temporal.toString();
        }
        return value;
    }

    /**
     * Entity collections stay outside capture. A list is kept only when a caller already
     * placed a plain value, such as the registered allergy active-ingredient list.
     */
    private static List<Object> plainCollection(Collection<?> collection) {
        List<Object> items = new ArrayList<>();
        for (Object item : collection) {
            if (item == null) {
                continue;
            }
            if (!(item instanceof Map<?, ?>) && !isSimple(item.getClass()) && !(item instanceof Enum<?>)) {
                return null;
            }
            Object normalized = normalize(item);
            if (normalized == null) {
                return null;
            }
            items.add(normalized);
        }
        return items;
    }

    private static Map<String, Object> plainMap(Map<?, ?> map) {
        Map<String, Object> plain = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                return null;
            }
            Object normalized = normalize(entry.getValue());
            if (entry.getValue() != null && normalized == null) {
                return null;
            }
            if (normalized != null) {
                plain.put(String.valueOf(entry.getKey()), normalized);
            }
        }
        return plain;
    }

    private String json(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to store amendment change_data", ex);
        }
    }
}
