package com.dazzle.asklepios.service.dto.storeProcedureDto;

import com.dazzle.asklepios.domain.enumeration.TestResultType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
@Getter
@Setter
public class IntegrationResultRequestDTO {

    private Long patientId;
    private Long orderTestId;
    private Long profileTestId;

    private TestResultType resultType;

    private BigDecimal resultValueNumber;
    private String resultValueText;

    private String marker;
    private String normalRangeValue;

    private String processingStatus;
    private String approvedBy;

}