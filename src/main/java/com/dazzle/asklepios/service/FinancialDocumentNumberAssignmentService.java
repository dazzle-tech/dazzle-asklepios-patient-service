package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.FinancialDocumentNumberingClient;
import com.dazzle.asklepios.client.setup.dto.FinancialDocumentNumberRequest;
import com.dazzle.asklepios.client.setup.dto.FinancialDocumentNumberResponse;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.repository.BillingPaymentRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FinancialDocumentNumberAssignmentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FinancialDocumentNumberAssignmentService.class);

    private static final String ENTITY_NAME = "financialDocumentNumbering";

    private final FinancialDocumentNumberingClient financialDocumentNumberingClient;
    private final FinancialDocumentRepository financialDocumentRepository;
    private final BillingPaymentRepository billingPaymentRepository;

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

        LocalDate effectiveDate =
                documentDate != null ? documentDate : LocalDate.now();

        try {
            FinancialDocumentNumberResponse response =
                    financialDocumentNumberingClient.generateNextNumber(
                            new FinancialDocumentNumberRequest(
                                    facilityId,
                                    documentType,
                                    effectiveDate,
                                    resolveMinimumUsedSequence(
                                            facilityId,
                                            documentType,
                                            effectiveDate
                                    )
                            )
                    );

            if (
                    response == null
                            || response.documentNumber() == null
                            || response.documentNumber().isBlank()
            ) {
                LOG.warn(
                        "Setup service returned an empty financial document number for facility {} and type {}",
                        facilityId,
                        documentType
                );
                return Optional.empty();
            }

            LOG.debug(
                    "Assigned financial document number from setup facilityId={} type={} periodKey={} sequence={} number={}",
                    facilityId,
                    documentType,
                    response.periodKey(),
                    response.sequenceNumber(),
                    response.documentNumber()
            );

            return Optional.of(response.documentNumber());
        } catch (FeignException.NotFound exception) {
            LOG.warn(
                    "Financial document numbering is not configured for facility {} and type {}",
                    facilityId,
                    documentType
            );
            return Optional.empty();
        } catch (FeignException.BadRequest exception) {
            throw mapSetupBadRequest(exception);
        } catch (FeignException exception) {
            LOG.error(
                    "Failed to allocate financial document number from setup for facility {} and type {}",
                    facilityId,
                    documentType,
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to allocate the next financial document number from setup.",
                    ENTITY_NAME,
                    "numbering.assign.failed"
            );
        }
    }

    private Long resolveMinimumUsedSequence(
            Long facilityId,
            FinancialDocumentType documentType,
            LocalDate documentDate
    ) {
        int year = documentDate.getYear();

        if (documentType == FinancialDocumentType.RECEIPT) {
            return billingPaymentRepository
                    .findMaxIssuedReceiptSequenceForYear(
                            facilityId,
                            year
                    )
                    .filter(value -> value > 0)
                    .orElse(null);
        }

        return financialDocumentRepository
                .findMaxIssuedSequenceForYear(
                        facilityId,
                        documentType.name(),
                        year
                )
                .filter(value -> value > 0)
                .orElse(null);
    }

    private BadRequestAlertException mapSetupBadRequest(
            FeignException.BadRequest exception
    ) {
        String message =
                exception.contentUTF8();

        if (message != null && message.contains("configuration.inactive")) {
            return new BadRequestAlertException(
                    "Financial document numbering is not active for this facility.",
                    ENTITY_NAME,
                    "numbering.configuration.inactive"
            );
        }

        if (message != null && message.contains("notfound")) {
            return new BadRequestAlertException(
                    "Financial document numbering is not configured for this facility and document type.",
                    ENTITY_NAME,
                    "numbering.configuration.notfound"
            );
        }

        return new BadRequestAlertException(
                "Unable to allocate the next financial document number from setup.",
                ENTITY_NAME,
                "numbering.assign.failed"
        );
    }
}

