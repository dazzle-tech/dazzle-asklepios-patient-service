package com.dazzle.asklepios.service.storeProcedure;

import com.dazzle.asklepios.client.setup.dto.NormalRangeMatchDTO;
import com.dazzle.asklepios.repository.storeProcedure.DiagnosticOrderTestResultProcedureRepository;
import com.dazzle.asklepios.service.DiagnosticOrderTestResultStatusService;
import com.dazzle.asklepios.service.NormalRangeMatcherService;
import com.dazzle.asklepios.service.dto.storeProcedureDto.IntegrationResultRequestDTO;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
@Transactional
public class DiagnosticIntegrationResultService {
    private static final Logger LOG =
            LoggerFactory.getLogger(DiagnosticIntegrationResultService.class);
    private final NormalRangeMatcherService normalRangeMatcherService;
    private final DiagnosticOrderTestResultProcedureRepository resultRepository;
    private final DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService;

    public DiagnosticIntegrationResultService(
            NormalRangeMatcherService normalRangeMatcherService,
            DiagnosticOrderTestResultProcedureRepository resultRepository,
            DiagnosticOrderTestResultStatusService diagnosticOrderTestResultStatusService
    ) {
        this.normalRangeMatcherService = normalRangeMatcherService;
        this.resultRepository = resultRepository;
        this.diagnosticOrderTestResultStatusService = diagnosticOrderTestResultStatusService;
    }

    public void process(IntegrationResultRequestDTO request) {

        LOG.info("[IntegrationResult] process started orderTestId={} profileTestId={} patientId={}",
                request.getOrderTestId(),
                request.getProfileTestId(),
                request.getPatientId());

        NormalRangeMatchDTO normalRange = null;

        String normalRangeValue = request.getNormalRangeValue();
        String marker = request.getMarker();

        LOG.debug("[IntegrationResult] incoming marker={} normalRange={}",
                marker,
                normalRangeValue);

        if (isBlank(normalRangeValue)) {

            LOG.debug("[IntegrationResult] normal range missing -> fetching best match");

            normalRange = normalRangeMatcherService.findBestNormalRange(
                    request.getProfileTestId(),
                    request.getPatientId()
            );

            normalRangeValue = buildNormalRangeValue(normalRange);

            LOG.debug("[IntegrationResult] resolved normal range={}",
                    normalRangeValue);
        }

        if (isBlank(marker)) {

            LOG.debug("[IntegrationResult] marker missing -> calculating");

            if (!isBlank(request.getNormalRangeValue())) {

                marker = calculateSimpleMarker(
                        request.getResultValueNumber(),
                        request.getNormalRangeValue()
                );

                LOG.debug("[IntegrationResult] simple marker calculated={}", marker);

            } else {

                if (normalRange == null) {

                    LOG.debug("[IntegrationResult] fetching normal range for advanced marker");

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

                LOG.debug("[IntegrationResult] advanced marker calculated={}", marker);
            }
        }

        LOG.info("[IntegrationResult] calling upsertResult orderTestId={} marker={} normalRange={}",
                request.getOrderTestId(),
                marker,
                normalRangeValue);

        resultRepository.upsertResult(
                request,
                marker,
                normalRangeValue
        );

        LOG.info("[IntegrationResult] upsertResult completed orderTestId={}",
                request.getOrderTestId());

        if (request.getOrderTestId() != null) {

            LOG.info("[IntegrationResult] recompute processing status orderTestId={}",
                    request.getOrderTestId());

            diagnosticOrderTestResultStatusService
                    .recomputeTestProcessingStatusFromResults(
                            request.getOrderTestId()
                    );

            LOG.info("[IntegrationResult] recompute completed orderTestId={}",
                    request.getOrderTestId());
        }

        LOG.info("[IntegrationResult] process finished orderTestId={}",
                request.getOrderTestId());
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

                return resultValue.compareTo(max) > 0
                        ? "UPPER_LIMIT"
                        : "NORMAL_MARKER";
            }

            if (range.startsWith(">")) {
                BigDecimal min = new BigDecimal(range.replace(">", "").trim());

                return resultValue.compareTo(min) < 0
                        ? "LOWER_LIMIT"
                        : "NORMAL_MARKER";
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