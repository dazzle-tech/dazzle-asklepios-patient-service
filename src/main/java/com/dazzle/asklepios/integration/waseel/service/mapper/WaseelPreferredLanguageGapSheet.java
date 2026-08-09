package com.dazzle.asklepios.integration.waseel.service.mapper;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Waseel GapSheet PreferredLanguage sheet (FHIR communication language codes).
 */
final class WaseelPreferredLanguageGapSheet {

    private WaseelPreferredLanguageGapSheet() {
    }

    static final Map<String, String> CODE_TO_CANONICAL_LANG_KEY = Map.ofEntries(
            Map.entry("ar", "ar"),
            Map.entry("bn", "bn"),
            Map.entry("cs", "cs"),
            Map.entry("da", "da"),
            Map.entry("de", "de"),
            Map.entry("de-at", "de"),
            Map.entry("de-ch", "de"),
            Map.entry("de-de", "de"),
            Map.entry("el", "el"),
            Map.entry("en", "en"),
            Map.entry("en-au", "en"),
            Map.entry("en-ca", "en"),
            Map.entry("en-gb", "en"),
            Map.entry("en-in", "en"),
            Map.entry("en-nz", "en"),
            Map.entry("en-sg", "en"),
            Map.entry("en-us", "en"),
            Map.entry("es", "es"),
            Map.entry("es-ar", "es"),
            Map.entry("es-es", "es"),
            Map.entry("es-uy", "es"),
            Map.entry("fi", "fi"),
            Map.entry("fr", "fr"),
            Map.entry("fr-be", "fr"),
            Map.entry("fr-ch", "fr"),
            Map.entry("fr-fr", "fr"),
            Map.entry("fy", "fy"),
            Map.entry("fy-nl", "fy"),
            Map.entry("hi", "hi"),
            Map.entry("hr", "hr"),
            Map.entry("it", "it"),
            Map.entry("it-ch", "it"),
            Map.entry("it-it", "it"),
            Map.entry("ja", "ja"),
            Map.entry("ko", "ko"),
            Map.entry("nl", "nl"),
            Map.entry("nl-be", "nl"),
            Map.entry("nl-nl", "nl"),
            Map.entry("no", "no"),
            Map.entry("no-no", "no"),
            Map.entry("pa", "pa"),
            Map.entry("pt", "pt"),
            Map.entry("pt-br", "pt"),
            Map.entry("ru", "ru"),
            Map.entry("ru-ru", "ru"),
            Map.entry("sr", "sr"),
            Map.entry("sr-rs", "sr"),
            Map.entry("sv", "sv"),
            Map.entry("sv-se", "sv"),
            Map.entry("te", "te"),
            Map.entry("tr", "tr"),
            Map.entry("zh", "zh"),
            Map.entry("zh-cn", "zh"),
            Map.entry("zh-hk", "zh"),
            Map.entry("zh-sg", "zh"),
            Map.entry("zh-tw", "zh")
    );

    /** ISO-639 / common setup {@code lang_key} variants that belong to a Waseel canonical code. */
    static final Map<String, Set<String>> SETUP_LANG_KEY_VARIANTS = Map.ofEntries(
            Map.entry("ar", Set.of("arb", "ara")),
            Map.entry("bn", Set.of("ben")),
            Map.entry("cs", Set.of("ces", "cze")),
            Map.entry("da", Set.of("dan")),
            Map.entry("de", Set.of("deu", "ger")),
            Map.entry("el", Set.of("ell", "gre")),
            Map.entry("en", Set.of("eng")),
            Map.entry("es", Set.of("spa")),
            Map.entry("fi", Set.of("fin")),
            Map.entry("fr", Set.of("fra", "fre")),
            Map.entry("hi", Set.of("hin")),
            Map.entry("hr", Set.of("hrv")),
            Map.entry("it", Set.of("ita")),
            Map.entry("ja", Set.of("jpn")),
            Map.entry("ko", Set.of("kor")),
            Map.entry("nl", Set.of("nld", "dut")),
            Map.entry("no", Set.of("nor")),
            Map.entry("pt", Set.of("por")),
            Map.entry("ru", Set.of("rus")),
            Map.entry("sr", Set.of("srp")),
            Map.entry("sv", Set.of("swe")),
            Map.entry("tr", Set.of("tur")),
            Map.entry("zh", Set.of("zho", "chi"))
    );

    /** English keywords from Waseel display names used to match setup {@code lang_name}. */
    static final Map<String, String> CANONICAL_TO_WASEEL_DISPLAY = Map.ofEntries(
            Map.entry("ar", "arabic"),
            Map.entry("bn", "bengali"),
            Map.entry("cs", "czech"),
            Map.entry("da", "danish"),
            Map.entry("de", "german"),
            Map.entry("el", "greek"),
            Map.entry("en", "english"),
            Map.entry("es", "spanish"),
            Map.entry("fi", "finnish"),
            Map.entry("fr", "french"),
            Map.entry("fy", "frysian"),
            Map.entry("hi", "hindi"),
            Map.entry("hr", "croatian"),
            Map.entry("it", "italian"),
            Map.entry("ja", "japanese"),
            Map.entry("ko", "korean"),
            Map.entry("nl", "dutch"),
            Map.entry("no", "norwegian"),
            Map.entry("pa", "punjabi"),
            Map.entry("pt", "portuguese"),
            Map.entry("ru", "russian"),
            Map.entry("sr", "serbian"),
            Map.entry("sv", "swedish"),
            Map.entry("te", "telegu"),
            Map.entry("tr", "turkish"),
            Map.entry("zh", "chinese")
    );

    static final Map<String, String> DISPLAY_NAME_TO_CANONICAL_LANG_KEY = Map.ofEntries(
            Map.entry("arabic", "ar"),
            Map.entry("bengali", "bn"),
            Map.entry("czech", "cs"),
            Map.entry("danish", "da"),
            Map.entry("german", "de"),
            Map.entry("german (austria)", "de"),
            Map.entry("german (switzerland)", "de"),
            Map.entry("german (germany)", "de"),
            Map.entry("greek", "el"),
            Map.entry("english", "en"),
            Map.entry("english (australia)", "en"),
            Map.entry("english (canada)", "en"),
            Map.entry("english (great britain)", "en"),
            Map.entry("english (india)", "en"),
            Map.entry("english (new zeland)", "en"),
            Map.entry("english (new zealand)", "en"),
            Map.entry("english (singapore)", "en"),
            Map.entry("english (united states)", "en"),
            Map.entry("spanish", "es"),
            Map.entry("spanish (argentina)", "es"),
            Map.entry("spanish (spain)", "es"),
            Map.entry("spanish (uruguay)", "es"),
            Map.entry("finnish", "fi"),
            Map.entry("french", "fr"),
            Map.entry("french (belgium)", "fr"),
            Map.entry("french (switzerland)", "fr"),
            Map.entry("french (france)", "fr"),
            Map.entry("frysian", "fy"),
            Map.entry("frysian (netherlands)", "fy"),
            Map.entry("hindi", "hi"),
            Map.entry("croatian", "hr"),
            Map.entry("italian", "it"),
            Map.entry("italian (switzerland)", "it"),
            Map.entry("italian (italy)", "it"),
            Map.entry("japanese", "ja"),
            Map.entry("korean", "ko"),
            Map.entry("dutch", "nl"),
            Map.entry("dutch (belgium)", "nl"),
            Map.entry("dutch (netherlands)", "nl"),
            Map.entry("norwegian", "no"),
            Map.entry("norwegian (norway)", "no"),
            Map.entry("punjabi", "pa"),
            Map.entry("portuguese", "pt"),
            Map.entry("portuguese (brazil)", "pt"),
            Map.entry("russian", "ru"),
            Map.entry("russian (russia)", "ru"),
            Map.entry("serbian", "sr"),
            Map.entry("serbian (serbia)", "sr"),
            Map.entry("swedish", "sv"),
            Map.entry("swedish (sweden)", "sv"),
            Map.entry("telegu", "te"),
            Map.entry("telugu", "te"),
            Map.entry("turkish", "tr"),
            Map.entry("chinese", "zh"),
            Map.entry("chinese (china)", "zh"),
            Map.entry("chinese (hong kong)", "zh"),
            Map.entry("chinese (singapore)", "zh"),
            Map.entry("chinese (taiwan)", "zh")
    );

    static String normalizeCode(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static String normalizeDisplayName(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static List<String> candidateLangKeys(String waseelValue) {
        if (waseelValue == null || waseelValue.isBlank()) {
            return List.of();
        }

        String trimmed = waseelValue.trim();
        String normalizedCode = normalizeCode(trimmed);
        String canonical = CODE_TO_CANONICAL_LANG_KEY.get(normalizedCode);

        if (canonical == null) {
            canonical = DISPLAY_NAME_TO_CANONICAL_LANG_KEY.get(normalizeDisplayName(trimmed));
        }

        if (canonical == null && normalizedCode.matches("[a-z]{2}(-[a-z]{2})?")) {
            canonical = normalizedCode;
        }

        if (normalizedCode.contains("-")) {
            if (canonical != null) {
                return List.of(normalizedCode, canonical);
            }
            return List.of(normalizedCode);
        }

        if (canonical != null) {
            return List.of(canonical);
        }

        return List.of(normalizedCode);
    }

    static String canonicalLangKey(String waseelValue) {
        if (waseelValue == null || waseelValue.isBlank()) {
            return null;
        }

        String trimmed = waseelValue.trim();
        String normalizedCode = normalizeCode(trimmed);
        String canonical = CODE_TO_CANONICAL_LANG_KEY.get(normalizedCode);

        if (canonical == null) {
            canonical = DISPLAY_NAME_TO_CANONICAL_LANG_KEY.get(normalizeDisplayName(trimmed));
        }

        if (canonical == null && normalizedCode.matches("[a-z]{2}(-[a-z]{2})?")) {
            canonical = normalizedCode;
        }

        return canonical;
    }

    static boolean setupLanguageMatchesCanonical(String setupLangKey, String setupLangName, String waseelCanonical) {
        if (setupLangKey == null || setupLangKey.isBlank() || waseelCanonical == null || waseelCanonical.isBlank()) {
            return false;
        }

        String normalizedSetupKey = normalizeCode(setupLangKey);
        String canonical = normalizeCode(waseelCanonical);

        if (normalizedSetupKey.equals(canonical)) {
            return true;
        }

        Set<String> variants = SETUP_LANG_KEY_VARIANTS.get(canonical);
        if (variants != null && variants.contains(normalizedSetupKey)) {
            return true;
        }

        if (setupLangName == null || setupLangName.isBlank()) {
            return false;
        }

        String waseelDisplay = CANONICAL_TO_WASEEL_DISPLAY.get(canonical);
        if (waseelDisplay != null) {
            String normalizedSetupName = normalizeDisplayName(setupLangName);
            if (normalizedSetupName.contains(waseelDisplay)) {
                return true;
            }
        }

        return "ar".equals(canonical) && containsArabicScript(setupLangName);
    }

    static String waseelCodeForSetupLanguage(String setupLangKey, String setupLangName) {
        if (setupLangKey == null || setupLangKey.isBlank()) {
            return null;
        }

        for (String canonical : CANONICAL_TO_WASEEL_DISPLAY.keySet()) {
            if (setupLanguageMatchesCanonical(setupLangKey, setupLangName, canonical)) {
                return canonical;
            }
        }

        return normalizeCode(setupLangKey);
    }

    private static boolean containsArabicScript(String value) {
        return value.codePoints().anyMatch(
                codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.ARABIC
        );
    }
}
