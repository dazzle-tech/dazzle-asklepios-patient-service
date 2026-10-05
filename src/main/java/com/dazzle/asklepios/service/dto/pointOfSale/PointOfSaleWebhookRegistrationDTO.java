package com.dazzle.asklepios.service.dto.pointOfSale;

import com.dazzle.asklepios.client.NamiCloud.dto.NamiGenerateWebhookUrlResponse;
import com.dazzle.asklepios.client.NamiCloud.dto.NamiRegisterWebhookResponse;

import java.io.Serializable;

public record PointOfSaleWebhookRegistrationDTO(

        String callbackUrl,

        NamiGenerateWebhookUrlResponse generateUrlResponse,

        NamiRegisterWebhookResponse registerWebhookResponse

) implements Serializable {
}

