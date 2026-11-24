package com.dazzle.asklepios.domain.enumeration;

public enum ToothSurface {
    MESIAL, DISTAL, BUCCAL, LINGUAL, OCCLUSAL, INCISAL, LABIAL, PALATAL, FACIAL, WHOLE_TOOTH;

    public static java.util.List<ToothSurface> normalize(java.util.Collection<ToothSurface> surfaces) {
        if (surfaces == null) {
            return new java.util.ArrayList<>();
        }
        return surfaces.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }
}
