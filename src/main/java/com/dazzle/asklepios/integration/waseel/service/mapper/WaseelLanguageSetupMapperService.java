package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.integration.waseel.client.LanguageSetupClient;
import com.dazzle.asklepios.integration.waseel.client.dto.LanguageResponseVM;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves Waseel preferred language values against language setup and stores {@code langKey}
 * on the patient (not language id), following the same setup lookup approach used for country.
 */
@Service
@RequiredArgsConstructor
public class WaseelLanguageSetupMapperService {

    private final LanguageSetupClient languageSetupClient;
    private final ApLovMapperService lovMapperService;

    public String mapWaseelToStoredLangKey(String waseelValue) {
        if (isBlank(waseelValue)) {
            return "";
        }

        List<LanguageResponseVM> configuredLanguages = loadConfiguredLanguages();
        if (configuredLanguages.isEmpty()) {
            return "";
        }

        for (String candidate : WaseelPreferredLanguageGapSheet.candidateLangKeys(waseelValue)) {
            String resolved = resolveConfiguredLangKey(configuredLanguages, candidate);
            if (!isBlank(resolved)) {
                return resolved;
            }
        }

        String byDisplayName = resolveByDisplayName(configuredLanguages, waseelValue.trim());
        if (!isBlank(byDisplayName)) {
            return byDisplayName;
        }

        String legacyLangKey = resolveLegacyLovLangKey(waseelValue.trim(), configuredLanguages);
        return legacyLangKey == null ? "" : legacyLangKey;
    }

    public String mapStoredLangKeyToWaseelCode(String storedLangKey) {
        if (isBlank(storedLangKey)) {
            return null;
        }

        List<LanguageResponseVM> configuredLanguages = loadConfiguredLanguages();
        LanguageResponseVM language = findConfiguredLanguage(configuredLanguages, storedLangKey.trim());
        if (language == null) {
            String legacyLangKey = resolveLegacyLovLangKey(storedLangKey.trim(), configuredLanguages);
            if (!isBlank(legacyLangKey)) {
                language = findConfiguredLanguage(configuredLanguages, legacyLangKey);
            }
        }
        if (language == null) {
            return null;
        }

        return WaseelPreferredLanguageGapSheet.waseelCodeForSetupLanguage(
                language.langKey(),
                language.langName()
        );
    }

    private List<LanguageResponseVM> loadConfiguredLanguages() {
        try {
            List<LanguageResponseVM> languages = languageSetupClient.getAllLanguages();
            return languages == null ? List.of() : languages;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String resolveConfiguredLangKey(List<LanguageResponseVM> languages, String candidate) {
        if (isBlank(candidate)) {
            return null;
        }

        String normalizedCandidate = WaseelPreferredLanguageGapSheet.normalizeCode(candidate);

        String exactMatch = languages.stream()
                .filter(Objects::nonNull)
                .filter(language -> !isBlank(language.langKey()))
                .filter(language ->
                        WaseelPreferredLanguageGapSheet.normalizeCode(language.langKey()).equals(normalizedCandidate))
                .map(LanguageResponseVM::langKey)
                .findFirst()
                .orElse(null);
        if (!isBlank(exactMatch)) {
            return exactMatch;
        }

        String directLookup = resolveByDirectLookup(candidate);
        if (!isBlank(directLookup)) {
            return directLookup;
        }

        String canonical = WaseelPreferredLanguageGapSheet.canonicalLangKey(candidate);
        if (isBlank(canonical)) {
            canonical = normalizedCandidate;
        }

        return resolveByWaseelCanonical(languages, canonical);
    }

    private String resolveByWaseelCanonical(List<LanguageResponseVM> languages, String waseelCanonical) {
        if (isBlank(waseelCanonical)) {
            return null;
        }

        return languages.stream()
                .filter(Objects::nonNull)
                .filter(language -> !isBlank(language.langKey()))
                .filter(language -> WaseelPreferredLanguageGapSheet.setupLanguageMatchesCanonical(
                        language.langKey(),
                        language.langName(),
                        waseelCanonical
                ))
                .map(LanguageResponseVM::langKey)
                .findFirst()
                .orElse(null);
    }

    private LanguageResponseVM findConfiguredLanguage(List<LanguageResponseVM> languages, String langKey) {
        if (isBlank(langKey)) {
            return null;
        }

        LanguageResponseVM fromList = languages.stream()
                .filter(Objects::nonNull)
                .filter(language -> !isBlank(language.langKey()))
                .filter(language -> language.langKey().trim().equalsIgnoreCase(langKey.trim()))
                .findFirst()
                .orElse(null);
        if (fromList != null) {
            return fromList;
        }

        try {
            return languageSetupClient.getLanguageByLangKey(langKey.trim());
        } catch (FeignException.NotFound ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveByDirectLookup(String candidate) {
        try {
            LanguageResponseVM language = languageSetupClient.getLanguageByLangKey(candidate);
            return language == null || isBlank(language.langKey()) ? null : language.langKey();
        } catch (FeignException.NotFound ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveByDisplayName(List<LanguageResponseVM> languages, String displayName) {
        String canonical = WaseelPreferredLanguageGapSheet.canonicalLangKey(displayName);
        if (!isBlank(canonical)) {
            String byCanonical = resolveByWaseelCanonical(languages, canonical);
            if (!isBlank(byCanonical)) {
                return byCanonical;
            }
        }

        String normalizedDisplay = WaseelPreferredLanguageGapSheet.normalizeDisplayName(displayName);

        return languages.stream()
                .filter(Objects::nonNull)
                .filter(language -> !isBlank(language.langKey()) && !isBlank(language.langName()))
                .filter(language ->
                        WaseelPreferredLanguageGapSheet.normalizeDisplayName(language.langName()).equals(normalizedDisplay))
                .map(LanguageResponseVM::langKey)
                .findFirst()
                .orElse(null);
    }

    private String resolveLegacyLovLangKey(String storedValue, List<LanguageResponseVM> languages) {
        String cleanValueCode = lovMapperService.getCleanValueCodeByLovCodeAndKey(
                AsklepiosLovCodes.LANG,
                storedValue
        );

        if (isBlank(cleanValueCode)) {
            return null;
        }

        String canonical = switch (cleanValueCode.trim().toUpperCase(Locale.ROOT)) {
            case "AR" -> "ar";
            case "ENG" -> "en";
            case "FRN" -> "fr";
            case "SPAN" -> "es";
            case "RUSS" -> "ru";
            case "PORT" -> "pt";
            case "TURK" -> "tr";
            default -> cleanValueCode.trim().toLowerCase(Locale.ROOT);
        };

        return resolveConfiguredLangKey(languages, canonical);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
