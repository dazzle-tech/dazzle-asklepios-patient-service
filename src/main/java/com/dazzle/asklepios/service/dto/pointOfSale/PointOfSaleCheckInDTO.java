package com.dazzle.asklepios.service.dto.pointOfSale;

import java.time.Instant;

public record PointOfSaleCheckInDTO(
        Long id,
        String userLogin,
        Long configurationId,
        String configurationName,
        Boolean active,
        Instant checkInDate,
        Instant checkOutDate
) {}