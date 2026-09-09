package com.dazzle.asklepios.service.dto.pointOfSale;

import java.io.Serializable;

public record PointOfSaleConfigurationDTO(

        Long id,

        String name,

        String clientId,
        String clientSecret,

        String terminalId,

        String terminalSerialNo,

        String terminalType,

        String counterNumber,

        String cashRegisterNo,

        Boolean isActive,
        Boolean occupied

) implements Serializable {
}
