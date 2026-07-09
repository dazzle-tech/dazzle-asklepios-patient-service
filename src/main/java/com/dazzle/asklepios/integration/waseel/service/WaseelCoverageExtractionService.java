package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.InsuranceCoverage;
import com.dazzle.asklepios.integration.waseel.dto.eligibility.response.EligibilityResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WaseelCoverageExtractionService {

    private final ObjectMapper objectMapper;

    public InsuranceCoverage extractCoverage(String responseJson) {

        try {
            EligibilityResponse response =
                    objectMapper.readValue(responseJson, EligibilityResponse.class);

            BigDecimal copaymentPercent = BigDecimal.ZERO;
            BigDecimal copaymentCap = BigDecimal.ZERO;

            if (response.coverages() == null || response.coverages().isEmpty()) {
                return new InsuranceCoverage(copaymentPercent, copaymentCap);
            }

            var coverage = response.coverages().get(0);

            Map<String, List<Map<String, Object>>> items =
                    (Map<String, List<Map<String, Object>>>) coverage.items();

            if (items == null) {
                return new InsuranceCoverage(copaymentPercent, copaymentCap);
            }

            // ✅ KEY مهم جدًا من Waseel JSON
            var healthCoverageList = items.get("Health Benefit Plan Coverage.");

            if (healthCoverageList == null) {
                return new InsuranceCoverage(copaymentPercent, copaymentCap);
            }

            for (Object obj : healthCoverageList) {

                Map item = (Map) obj;
                List<Map<String, Object>> benefits =
                        (List<Map<String, Object>>) item.get("benefits");

                if (benefits == null) continue;

                for (Map<String, Object> benefit : benefits) {

                    String typeDisplay = benefit.get("typeDisplay") == null
                            ? ""
                            : benefit.get("typeDisplay").toString();

                    String value = benefit.get("value") == null
                            ? null
                            : benefit.get("value").toString();

                    if (value == null) continue;

                    // ✅ Copayment %
                    if (typeDisplay.equalsIgnoreCase("Copayment Percent per service.") ||
                            typeDisplay.equalsIgnoreCase("Copayment Percent per service")) {

                        copaymentPercent = new BigDecimal(value);
                    }

                    // ✅ Cap
                    if (typeDisplay.equalsIgnoreCase("Copayment maximum per service.") ||
                            typeDisplay.equalsIgnoreCase("Copayment maximum per service")) {

                        copaymentCap = new BigDecimal(value);
                    }
                }
            }

            return new InsuranceCoverage(copaymentPercent, copaymentCap);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract coverage from Waseel response", e);
        }
    }
}