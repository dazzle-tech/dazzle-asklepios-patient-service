package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.enumeration.ToothSurface;
import com.dazzle.asklepios.integration.waseel.service.readiness.PreAuthorizationClaimType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DentalSiteTest {

    @Test
    void oneToothIsTheBodySite() {
        assertThat(DentalSite.of(List.of("16"), "O")).isEqualTo(new DentalSite("16", "O"));
        assertThat(DentalSite.of(List.of("54"), "MOD")).isEqualTo(new DentalSite("54", "MOD"));
    }

    @Test
    void severalTeethAreSentWithoutABodySiteAndSoWithoutASurface() {
        assertThat(DentalSite.of(List.of("54", "61"), "O")).isEqualTo(DentalSite.NONE);
    }

    @Test
    void aSurfaceIsNeverSentWithoutItsTooth() {
        assertThat(DentalSite.of("UR1", ToothSurface.OCCLUSAL)).isEqualTo(DentalSite.NONE);
        assertThat(DentalSite.of(List.of(), "M")).isEqualTo(DentalSite.NONE);
    }

    @Test
    void onlyFdiTeethAreSent() {
        assertThat(DentalSite.fdiTooth("48")).isEqualTo("48");
        assertThat(DentalSite.fdiTooth("85")).isEqualTo("85");
        assertThat(DentalSite.fdiTooth("19")).isNull();
        assertThat(DentalSite.fdiTooth("86")).isNull();
        assertThat(DentalSite.fdiTooth("UR1")).isNull();
    }

    @Test
    void surfacesFollowTheNphiesList() {
        assertThat(DentalSite.nphiesSurface("M")).isEqualTo("M");
        assertThat(DentalSite.nphiesSurface("do")).isEqualTo("DO");
        assertThat(DentalSite.nphiesSurface("F")).isEqualTo("V");
        assertThat(DentalSite.nphiesSurface("I_D")).isEqualTo("DI");
        assertThat(DentalSite.nphiesSurface("MOB")).isNull();
        assertThat(DentalSite.nphiesSurface("UR")).isNull();
        assertThat(DentalSite.nphiesSurface(null)).isNull();
    }

    @Test
    void dentalChartSurfacesAreMapped() {
        assertThat(DentalSite.of("21", ToothSurface.INCISAL)).isEqualTo(new DentalSite("21", "I"));
        assertThat(DentalSite.of("36", ToothSurface.FACIAL)).isEqualTo(new DentalSite("36", "V"));
        assertThat(DentalSite.of("36", ToothSurface.WHOLE_TOOTH)).isEqualTo(new DentalSite("36", null));
    }

    @Test
    void dentalClaimIsSentAsOralOp() {
        assertThat(PreAuthorizationClaimType.ORAL_OP.nphiesType()).isEqualTo("oral");
        assertThat(PreAuthorizationClaimType.ORAL_OP.nphiesSubType()).isEqualTo("op");
        assertThat(PreAuthorizationClaimType.PROFESSIONAL_OP.nphiesType()).isEqualTo("professional");
    }
}
