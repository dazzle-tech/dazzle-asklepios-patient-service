package com.dazzle.asklepios.service.dto.billing;

import java.io.Serializable;
import java.math.BigDecimal;

public record DiscountCreditNoteLinePreview(

        Long documentItemId,

        String itemCode,

        String itemDescription,

        BigDecimal lineRemainingBefore,

        BigDecimal discountAmount,

        BigDecimal lineRemainingAfter

) implements Serializable {
}
