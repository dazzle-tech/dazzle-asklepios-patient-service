package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.ApLovValue;
import com.dazzle.asklepios.repository.ApLovValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApLovMapperService {

    private final ApLovValueRepository apLovValueRepository;

    private static final Map<String, String> MARITAL_STATUS_TO_NPHIES = Map.of(
            "MARRIED", "M",
            "SINGLE", "U",
            "DIVORCED", "D",
            "WIDOWED", "W"
    );

    private static final Map<String, String> OCCUPATION_TO_NPHIES = Map.ofEntries(
            Map.entry("NURSE", "medical field"),
            Map.entry("DOCTOR", "medical field"),
            Map.entry("PHAR", "medical field"),
            Map.entry("TECH", "medical field"),
            Map.entry("DEN", "medical field"),
            Map.entry("OCCTH", "medical field"),
            Map.entry("RAD", "medical field"),
            Map.entry("PHD", "medical field"),

            Map.entry("ENGINEER", "skilled worker"),
            Map.entry("DEV", "skilled worker"),
            Map.entry("IT", "skilled worker"),
            Map.entry("TRAD", "skilled worker"),

            Map.entry("EDU", "education"),
            Map.entry("AGRI", "agriculture"),

            Map.entry("FIN", "business"),
            Map.entry("BUSI", "business"),
            Map.entry("RET", "business"),

            Map.entry("PUBSER", "administration"),

            Map.entry("ART", "others"),
            Map.entry("HOSP", "others")
    );

    public String getKeyByLovCodeAndValueCode(String lovCode, String valueCode) {
        if (isBlank(lovCode) || isBlank(valueCode)) {
            return "";
        }

        List<ApLovValue> values = apLovValueRepository.findByLovCodeAndIsValidTrue(lovCode);
        String normalizedValueCode = normalize(valueCode);

        return values.stream()
                .filter(v -> normalize(v.getValueCode()).equals(normalizedValueCode))
                .map(ApLovValue::getKey)
                .findFirst()
                .orElse("");
    }

    public String getValueCodeByLovCodeAndKey(String lovCode, String key) {
        if (isBlank(lovCode) || isBlank(key)) {
            return null;
        }

        List<ApLovValue> values = apLovValueRepository.findByLovCodeAndIsValidTrue(lovCode);
        String normalizedKey = normalizeKey(key);

        return values.stream()
                .filter(v -> normalizeKey(v.getKey()).equals(normalizedKey))
                .map(ApLovValue::getValueCode)
                .findFirst()
                .orElse(null);
    }

    public String mapMaritalStatusKeyToNphies(String maritalStatusKey) {
        String valueCode = getValueCodeByLovCodeAndKey(
                AsklepiosLovCodes.MARITAL_STATUS,
                maritalStatusKey
        );

        return mapMaritalStatusValueCodeToNphies(valueCode);
    }

    public String mapOccupationKeyToNphies(String occupationKey) {
        String valueCode = getValueCodeByLovCodeAndKey(
                AsklepiosLovCodes.OCCUPATION,
                occupationKey
        );

        return mapOccupationValueCodeToNphies(valueCode);
    }

    public String mapMaritalStatusValueCodeToNphies(String valueCode) {
        String normalized = normalize(valueCode);

        if (normalized.isEmpty()) {
            return null;
        }

        return MARITAL_STATUS_TO_NPHIES.getOrDefault(normalized, normalized);
    }

    public String mapOccupationValueCodeToNphies(String valueCode) {
        String normalized = normalize(valueCode);

        if (normalized.isEmpty()) {
            return null;
        }

        return OCCUPATION_TO_NPHIES.getOrDefault(
                normalized,
                normalized.toLowerCase(Locale.ROOT)
        );
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .replace("_", " ")
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}