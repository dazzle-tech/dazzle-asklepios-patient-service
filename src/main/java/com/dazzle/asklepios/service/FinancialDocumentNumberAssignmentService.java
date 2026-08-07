package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialDocumentNumberAssignmentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FinancialDocumentNumberAssignmentService.class);

    private static final String ENTITY_NAME = "financialDocumentNumbering";

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public String requireNextDocumentNumber(
            Long facilityId,
            FinancialDocumentType documentType,
            LocalDate documentDate
    ) {
        return assignNextDocumentNumber(facilityId, documentType, documentDate)
                .orElseThrow(
                        () ->
                                new BadRequestAlertException(
                                        "Unable to assign a financial document number for facility "
                                                + facilityId
                                                + " and document type "
                                                + documentType,
                                        ENTITY_NAME,
                                        "numbering.assign.failed"
                                )
                );
    }

    @Transactional
    public Optional<String> assignNextDocumentNumber(
            Long facilityId,
            FinancialDocumentType documentType,
            LocalDate documentDate
    ) {
        if (facilityId == null || documentType == null) {
            return Optional.empty();
        }

        NumberingConfig config = loadActiveConfig(facilityId, documentType.name());
        if (config == null) {
            LOG.warn(
                    "Active financial document numbering is not configured for facility {} and type {}",
                    facilityId,
                    documentType
            );
            return Optional.empty();
        }

        LocalDate effectiveDate =
                documentDate != null ? documentDate : LocalDate.now();

        String periodKey = resolvePeriodKey(config.resetFrequency(), effectiveDate);
        long nextSequence = reserveNextSequence(
                facilityId,
                documentType.name(),
                periodKey,
                config.startingNumber()
        );

        String documentNumber = formatDocumentNumber(
                config,
                nextSequence,
                effectiveDate,
                facilityId
        );

        LOG.debug(
                "Assigned financial document number facilityId={} type={} periodKey={} sequence={} number={}",
                facilityId,
                documentType,
                periodKey,
                nextSequence,
                documentNumber
        );

        return Optional.of(documentNumber);
    }

    private NumberingConfig loadActiveConfig(Long facilityId, String documentType) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                    SELECT prefix,
                           sequence_length,
                           include_year,
                           include_facility_code,
                           number_separator,
                           reset_frequency,
                           starting_number
                    FROM financial_document_numbering
                    WHERE facility_id = ?
                      AND document_type = ?
                      AND active = TRUE
                      AND status = 'ACTIVE'
                    """,
                    (rs, rowNum) ->
                            new NumberingConfig(
                                    rs.getString("prefix"),
                                    rs.getInt("sequence_length"),
                                    rs.getBoolean("include_year"),
                                    rs.getBoolean("include_facility_code"),
                                    rs.getString("number_separator"),
                                    rs.getString("reset_frequency"),
                                    rs.getLong("starting_number")
                            ),
                    facilityId,
                    documentType
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private long reserveNextSequence(
            Long facilityId,
            String documentType,
            String periodKey,
            long startingNumber
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO financial_document_sequence (
                    facility_id,
                    document_type,
                    period_key,
                    last_number,
                    created_by,
                    created_date
                ) VALUES (?, ?, ?, ?, 'system', NOW())
                ON CONFLICT (facility_id, document_type, period_key) DO NOTHING
                """,
                facilityId,
                documentType,
                periodKey,
                startingNumber - 1
        );

        Long nextNumber = jdbcTemplate.queryForObject(
                """
                UPDATE financial_document_sequence
                SET last_number = last_number + 1,
                    last_modified_by = 'system',
                    last_modified_date = NOW()
                WHERE facility_id = ?
                  AND document_type = ?
                  AND period_key = ?
                RETURNING last_number
                """,
                Long.class,
                facilityId,
                documentType,
                periodKey
        );

        if (nextNumber == null) {
            throw new BadRequestAlertException(
                    "Unable to reserve the next financial document sequence for facility "
                            + facilityId
                            + ", document type "
                            + documentType
                            + ", period "
                            + periodKey,
                    ENTITY_NAME,
                    "sequence.reserve.failed"
            );
        }

        return nextNumber;
    }

    private String resolvePeriodKey(String resetFrequency, LocalDate documentDate) {
        return switch (String.valueOf(resetFrequency).toUpperCase(Locale.ROOT)) {
            case "NEVER" -> "ALL";
            case "YEARLY" -> String.valueOf(documentDate.getYear());
            case "MONTHLY" ->
                    documentDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "DAILY" -> documentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            default -> String.valueOf(documentDate.getYear());
        };
    }

    private String formatDocumentNumber(
            NumberingConfig config,
            long sequenceNumber,
            LocalDate documentDate,
            Long facilityId
    ) {
        String separator =
                config.numberSeparator() == null ? "" : config.numberSeparator().trim();

        List<String> parts = new ArrayList<>();
        parts.add(config.prefix().trim().toUpperCase(Locale.ROOT));

        if (config.includeFacilityCode()) {
            String facilityCode = loadFacilityCode(facilityId);
            if (facilityCode != null && !facilityCode.isBlank()) {
                parts.add(facilityCode.trim());
            }
        }

        if (config.includeYear()) {
            parts.add(String.valueOf(documentDate.getYear()));
        }

        parts.add(
                String.format(
                        Locale.ROOT,
                        "%0" + config.sequenceLength() + "d",
                        sequenceNumber
                )
        );

        return String.join(separator, parts);
    }

    private String loadFacilityCode(Long facilityId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT code FROM facility WHERE id = ?",
                    String.class,
                    facilityId
            );
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private record NumberingConfig(
            String prefix,
            int sequenceLength,
            boolean includeYear,
            boolean includeFacilityCode,
            String numberSeparator,
            String resetFrequency,
            long startingNumber
    ) {
    }
}
