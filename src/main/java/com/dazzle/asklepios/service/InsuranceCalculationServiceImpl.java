package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.InsuranceSplit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InsuranceCalculationServiceImpl implements InsuranceCalculationService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Override
    public InsuranceSplit calculateSplit(
            BigDecimal totalAmount,
            BigDecimal copaymentPercent,
            BigDecimal copaymentCap
    ) {

        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (copaymentPercent == null) copaymentPercent = BigDecimal.ZERO;
        if (copaymentCap == null) copaymentCap = BigDecimal.ZERO;

        BigDecimal percentageAmount =
                totalAmount.multiply(copaymentPercent)
                        .divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP);

        BigDecimal patientShare =
                percentageAmount.min(copaymentCap);

        BigDecimal insuranceShare =
                totalAmount.subtract(patientShare);

        return new InsuranceSplit(patientShare, insuranceShare);
    }
}
