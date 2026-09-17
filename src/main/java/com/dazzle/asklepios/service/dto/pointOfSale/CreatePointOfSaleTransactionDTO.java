package com.dazzle.asklepios.service.dto.pointOfSale;

import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleSourceType;
import com.dazzle.asklepios.domain.enumeration.pointOfSale.PointOfSaleTransactionType;

import java.io.Serializable;
import java.math.BigDecimal;

public record CreatePointOfSaleTransactionDTO(

        Long patientId,

        PointOfSaleSourceType sourceType,

        Long sourceReferenceId,

        BigDecimal amount,

        PointOfSaleTransactionType transactionType

) implements Serializable {
}