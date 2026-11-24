package com.dazzle.asklepios.domain.converter;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ToothSurfaceListConverterTest {

    private final ToothSurfaceListConverter converter = new ToothSurfaceListConverter();

    @Test
    void storesSurfacesAsCommaSeparatedNames() {
        assertThat(converter.convertToDatabaseColumn(List.of(ToothSurface.MESIAL, ToothSurface.OCCLUSAL)))
                .isEqualTo("MESIAL,OCCLUSAL");
    }

    @Test
    void dropsNullAndDuplicateSurfacesWhenStoring() {
        assertThat(converter.convertToDatabaseColumn(
                Arrays.asList(ToothSurface.DISTAL, null, ToothSurface.DISTAL, ToothSurface.LABIAL)))
                .isEqualTo("DISTAL,LABIAL");
    }

    @Test
    void storesNullForNullOrEmptyCollections() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn(List.of())).isNull();
    }

    @Test
    void readsExistingSingleSurfaceValueAsOneItemList() {
        assertThat(converter.convertToEntityAttribute("MESIAL")).containsExactly(ToothSurface.MESIAL);
    }

    @Test
    void readsSeveralSurfaces() {
        assertThat(converter.convertToEntityAttribute("MESIAL, PALATAL,WHOLE_TOOTH"))
                .containsExactly(ToothSurface.MESIAL, ToothSurface.PALATAL, ToothSurface.WHOLE_TOOTH);
    }

    @Test
    void readsNullAndBlankAsEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThat(converter.convertToEntityAttribute(" ")).isEmpty();
    }

    @Test
    void roundTripsAllSurfacesWithinTheColumnLength() {
        String stored = converter.convertToDatabaseColumn(List.of(ToothSurface.values()));

        assertThat(stored.length()).isLessThanOrEqualTo(255);
        assertThat(converter.convertToEntityAttribute(stored)).containsExactly(ToothSurface.values());
    }
}
