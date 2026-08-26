package com.dazzle.asklepios.client.NamiCloud.dto;

import java.time.LocalDateTime;

public record NamiRegisterTerminalResponse(

        String status,

        String message,

        String terminalId,

        String terminalSlNo,

        LocalDateTime registrationDate

) {
}
