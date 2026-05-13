package com.dazzle.asklepios.service.storeProcedure;

import com.dazzle.asklepios.client.setup.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.repository.storeProcedure.DiagnosticOrderTestResultProcedureRepository;
import com.dazzle.asklepios.service.NormalRangeMatcherService;
import com.dazzle.asklepios.service.dto.storeProcedureDto.IntegrationResultRequestDTO;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Transactional
public class DiagnosticIntegrationResultService {

    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderTestResultProcedureRepository resultRepository;

    public DiagnosticIntegrationResultService(
            NormalRangeMatcherService normalRangeMatcherService,
            DiagnosticOrderTestResultProcedureRepository resultRepository
    ) {
        this.normalRangeMatcherService = normalRangeMatcherService;
        this.resultRepository = resultRepository;
    }

    public void process(IntegrationResultRequestDTO request) {

        NormalRangeMatchDTO normalRange = null;

        String normalRangeValue = request.getNormalRangeValue();
        String marker = request.getMarker();

        if (isBlank(normalRangeValue)) {
            normalRange = normalRangeMatcherService.findBestNormalRange(
                    request.getProfileTestId(),
                    request.getPatientId()
            );

            normalRangeValue = buildNormalRangeValue(normalRange);
        }


        if (isBlank(marker)) {

            if (!isBlank(request.getNormalRangeValue())) {
                marker = calculateSimpleMarker(
                        request.getResultValueNumber(),
                        request.getNormalRangeValue()
                );
            }

            else {
                if (normalRange == null) {
                    normalRange = normalRangeMatcherService.findBestNormalRange(
                            request.getProfileTestId(),
                            request.getPatientId()
                    );
                }

                marker = NormalRangeMatcherService.calculateMarker(
                        request.getResultType(),
                        request.getResultValueNumber(),
                        request.getResultValueText(),
                        normalRange
                ).name();
            }
        }

        // 4. خزني النتيجة
        resultRepository.upsertResult(
                request,
                marker,
                normalRangeValue
        );
    }

    private String buildNormalRangeValue(NormalRangeMatchDTO range) {
        if (range == null) {
            return null;
        }

        if (range.normalRangeType() == null) {
            return range.rangeFrom() + " - " + range.rangeTo();
        }

        return switch (range.normalRangeType()) {
            case RANGE -> range.rangeFrom() + " - " + range.rangeTo();
            case LESS_THAN -> "< " + range.rangeTo();
            case MORE_THAN -> "> " + range.rangeFrom();
        };
    }

    private String calculateSimpleMarker(BigDecimal resultValue, String normalRangeValue) {
        if (resultValue == null || isBlank(normalRangeValue)) {
            return "UNKNOWN";
        }

        String range = normalRangeValue.trim();

        try {
            if (range.startsWith("<")) {
                BigDecimal max = new BigDecimal(range.replace("<", "").trim());

                if (resultValue.compareTo(max) > 0) {
                    return "UPPER_LIMIT";
                }

                return "NORMAL_MARKER";
            }

            if (range.startsWith(">")) {
                BigDecimal min = new BigDecimal(range.replace(">", "").trim());

                if (resultValue.compareTo(min) < 0) {
                    return "LOWER_LIMIT";
                }

                return "NORMAL_MARKER";
            }

            if (range.contains("-")) {
                String[] parts = range.split("-");

                BigDecimal min = new BigDecimal(parts[0].trim());
                BigDecimal max = new BigDecimal(parts[1].trim());

                if (resultValue.compareTo(min) < 0) {
                    return "LOWER_LIMIT";
                }

                if (resultValue.compareTo(max) > 0) {
                    return "UPPER_LIMIT";
                }

                return "NORMAL_MARKER";
            }

            return "UNKNOWN";

        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}