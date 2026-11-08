package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.NphiesPayerClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.NphiesPayerDTO;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PayorHelperTest {

    @Mock
    private PayorClient payorClient;

    @Mock
    private NphiesPayerClient nphiesPayerClient;

    private PayorHelper payorHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payorHelper = new PayorHelper(payorClient, nphiesPayerClient);
    }

    @Test
    void resolvePayorId_whenPayorIdIsNphiesPayer_createsPayorFromNphies() {
        PayorDTO created = payor(44L, "INS-BUPA");

        when(payorClient.existsPayor(1018L)).thenThrow(notFound());
        when(nphiesPayerClient.getNphiesPayerById(1018L))
                .thenReturn(new NphiesPayerDTO(1018L, "INS-BUPA", "Bupa Arabia", null, true));
        when(payorClient.getPayorByNphiesId("INS-BUPA")).thenThrow(notFound());
        when(payorClient.ensurePayorFromNphiesId("INS-BUPA")).thenReturn(created);

        Long resolved = payorHelper.resolvePayorId(1018L, null);

        assertThat(resolved).isEqualTo(44L);
    }

    @Test
    void resolvePayorId_whenPayorIdMissing_usesProvidedNphiesId() {
        when(payorClient.getPayorByNphiesId("INS-BUPA")).thenReturn(payor(7L, "INS-BUPA"));

        Long resolved = payorHelper.resolvePayorId(null, "INS-BUPA");

        assertThat(resolved).isEqualTo(7L);
    }

    private static PayorDTO payor(Long id, String nphiesId) {
        return new PayorDTO(
                id,
                "NPH-BUPA",
                "Bupa Arabia",
                nphiesId,
                nphiesId,
                null,
                false,
                null,
                null,
                true
        );
    }

    private static FeignException.NotFound notFound() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/setup/payor/1018",
                Collections.emptyMap(),
                null,
                new RequestTemplate()
        );
        return new FeignException.NotFound("404", request, new byte[0], Collections.emptyMap());
    }
}
