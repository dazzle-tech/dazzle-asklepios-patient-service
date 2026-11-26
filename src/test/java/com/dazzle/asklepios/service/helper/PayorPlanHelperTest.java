package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.PayorPlanClient;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayorPlanHelperTest {

    @Mock
    private PayorPlanClient payorPlanClient;

    private PayorPlanHelper payorPlanHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payorPlanHelper = new PayorPlanHelper(payorPlanClient);
    }

    @Test
    void resolvePlanId_manualInsuranceWithoutCchiFields_skipsCchiMatch() {
        Long planId = payorPlanHelper.resolvePlanId(24L, null, null, "A", null);

        assertThat(planId).isNull();
        verify(payorPlanClient, never()).getPayorPlanByCchiMatch(anyLong(), any(), any(), any());
    }

    @Test
    void resolvePlanId_cchiMatchBadRequest_returnsNull() {
        when(payorPlanClient.getPayorPlanByCchiMatch(24L, "IP", null, "A")).thenThrow(badRequest());

        Long planId = payorPlanHelper.resolvePlanId(24L, null, null, "A", "IP");

        assertThat(planId).isNull();
    }

    @Test
    void resolvePlanId_cchiMatchFound_returnsPlanId() {
        when(payorPlanClient.getPayorPlanByCchiMatch(24L, "IP", "NET-1", "A"))
                .thenReturn(new PayorPlanDTO(9L, 24L, "Class A", null, "NET-1", "IP", "2021", null, true));

        Long planId = payorPlanHelper.resolvePlanId(24L, null, "NET-1", "A", "IP");

        assertThat(planId).isEqualTo(9L);
    }

    private static FeignException.BadRequest badRequest() {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/setup/payor-plan/cchi/by-payor/24/match",
                Collections.emptyMap(),
                null,
                new RequestTemplate()
        );
        return new FeignException.BadRequest("400", request, new byte[0], Collections.emptyMap());
    }
}
