package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NphiesPayerHelperTest {

    @Mock
    private NphiesPayerClient nphiesPayerClient;

    @Mock
    private PayorHelper payorHelper;

    private NphiesPayerHelper helper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        helper = new NphiesPayerHelper(nphiesPayerClient, payorHelper);
    }

    @Test
    void resolvePayerId_usesStoredPayersIdWithoutRemoteLookups() {
        PatientInsurance insurance = new PatientInsurance();
        insurance.setNphiesPayerId(1018L);
        insurance.setPayorId(44L);
        insurance.setPayerNphiesId("INS-BUPA");

        assertThat(helper.resolvePayerId(insurance)).isEqualTo(1018L);
        verifyNoInteractions(nphiesPayerClient, payorHelper);
    }

    @Test
    void resolvePayerId_withoutStoredId_fallsBackToNphiesIdLookup() {
        PatientInsurance insurance = new PatientInsurance();
        insurance.setPayorId(44L);
        insurance.setPayerNphiesId("INS-BUPA");
        when(nphiesPayerClient.getNphiesPayersByNphiesId("INS-BUPA", 0, 1))
                .thenReturn(List.of(new NphiesPayerDTO(1018L, "INS-BUPA", "Bupa Arabia", null, true, null, true)));

        assertThat(helper.resolvePayerId(insurance)).isEqualTo(1018L);
        verify(payorHelper, never()).findPayor(any(), any());
    }

    @Test
    void resolvePayerIdForWrite_explicitPayerIdWinsOverLegacyPayor() {
        when(nphiesPayerClient.getNphiesPayerById(1018L))
                .thenReturn(new NphiesPayerDTO(1018L, "INS-BUPA", "Bupa Arabia", null, true, null, true));

        assertThat(helper.resolvePayerId(1018L, 44L, "INS-OTHER")).isEqualTo(1018L);
        verifyNoInteractions(payorHelper);
    }

    @Test
    void resolvePayerIdForWrite_unknownExplicitPayerId_returnsNull() {
        when(nphiesPayerClient.getNphiesPayerById(999L)).thenReturn(null);

        assertThat(helper.resolvePayerId(999L, null, null)).isNull();
    }

    @Test
    void resolvePayerIdForWrite_withoutPayerId_resolvesFromNphiesId() {
        when(nphiesPayerClient.getNphiesPayersByNphiesId("INS-BUPA", 0, 1))
                .thenReturn(List.of(new NphiesPayerDTO(1018L, "INS-BUPA", "Bupa Arabia", null, true, null, true)));

        assertThat(helper.resolvePayerId(null, null, "INS-BUPA")).isEqualTo(1018L);
    }

    @Test
    void resolvePayerId_nullInsurance_returnsNull() {
        assertThat(helper.resolvePayerId(null)).isNull();
        verify(nphiesPayerClient, never()).getNphiesPayersByNphiesId(any(), anyInt(), anyInt());
    }
}
