package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where a dental item was done, in NPHIES codes: {@code bodySite} is the FDI tooth
 * (GapSheet "fdi-oral-region", 11-48 permanent, 51-85 primary) and {@code subSite} the tooth surface
 * (LOV "SubSite for approval/claim type = oral": M, O, I, D, B, V, L, MO, DO, DI, MOD).
 * A value NPHIES does not list is not sent.
 */
public record DentalSite(String bodySite, String subSite) {

    public static final DentalSite NONE = new DentalSite(null, null);

    private static final Pattern FDI_TOOTH = Pattern.compile("[1-4][1-8]|[5-8][1-5]");

    private static final Set<String> NPHIES_SURFACES = Set.of("M", "O", "I", "D", "B", "V", "L", "MO", "DO", "DI", "MOD");

    /** System surface codes (LOV TOOTH_SURF) that NPHIES names differently. */
    private static final Map<String, String> SURFACE_ALIASES = Map.of(
            "F", "V",      // facial = surface facing the lips
            "I_D", "DI",   // incisal-distal
            "I-D", "DI"
    );

    private static final Map<ToothSurface, String> CHART_SURFACES = Map.of(
            ToothSurface.MESIAL, "M",
            ToothSurface.DISTAL, "D",
            ToothSurface.BUCCAL, "B",
            ToothSurface.LINGUAL, "L",
            ToothSurface.OCCLUSAL, "O",
            ToothSurface.INCISAL, "I",
            ToothSurface.FACIAL, "V"
    );

    /**
     * One tooth is one body site. A line covering several teeth is sent without a tooth,
     * since NPHIES takes a single body site per item.
     */
    public static DentalSite of(List<String> toothNumbers, String surfaceCode) {
        List<String> teeth = toothNumbers == null
                ? List.of()
                : toothNumbers.stream().filter(Objects::nonNull).map(String::trim).filter(tooth -> !tooth.isEmpty()).toList();
        return withSurfaceOnTooth(teeth.size() == 1 ? fdiTooth(teeth.get(0)) : null, nphiesSurface(surfaceCode));
    }

    public static DentalSite of(String toothNumber, ToothSurface surface) {
        return withSurfaceOnTooth(fdiTooth(toothNumber), surface == null ? null : CHART_SURFACES.get(surface));
    }

    /** NPHIES: "The subSite shall not be allowed if the bodySite is not provided". */
    private static DentalSite withSurfaceOnTooth(String bodySite, String subSite) {
        return new DentalSite(bodySite, bodySite == null ? null : subSite);
    }

    static String fdiTooth(String toothNumber) {
        String tooth = toothNumber == null ? "" : toothNumber.trim();
        return FDI_TOOTH.matcher(tooth).matches() ? tooth : null;
    }

    static String nphiesSurface(String surfaceCode) {
        if (surfaceCode == null || surfaceCode.isBlank()) {
            return null;
        }
        String code = surfaceCode.trim().toUpperCase(Locale.ROOT);
        String nphies = SURFACE_ALIASES.getOrDefault(code, code);
        return NPHIES_SURFACES.contains(nphies) ? nphies : null;
    }
}
