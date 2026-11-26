package com.dazzle.asklepios.client.NamiCloud.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NamiTerminalStatusResponse(

        String terminalId,

        String terminalSlNo,

        String terminalType,

        String counterNumber,

        String merchantId,

        String connectionStatus,

        String registeredStatus,

        String registrationDate,

        String responseCode,

        String responseMessage

) {
}
