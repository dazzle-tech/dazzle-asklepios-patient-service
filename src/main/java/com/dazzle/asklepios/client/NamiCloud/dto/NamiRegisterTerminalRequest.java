package com.dazzle.asklepios.client.NamiCloud.dto;

public record NamiRegisterTerminalRequest(

        String terminalSlNo,

        String terminalType,

        String counterNumber,

        String clientId,

        String trxnType

) {
}
