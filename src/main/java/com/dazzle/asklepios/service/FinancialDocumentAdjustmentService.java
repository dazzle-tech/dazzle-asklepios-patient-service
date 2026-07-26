package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.domain.PatientLedgerEntry;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentItemAdjustmentAction;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentStatus;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentSubtype;
import com.dazzle.asklepios.domain.enumeration.FinancialDocumentType;
import com.dazzle.asklepios.domain.enumeration.LedgerAccount;
import com.dazzle.asklepios.domain.enumeration.LedgerEntryType;
import com.dazzle.asklepios.domain.enumeration.LedgerSource;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientLedgerRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.billing.AddableChargeLineResponse;
import com.dazzle.asklepios.service.dto.billing.CreateFinancialDocumentAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentItemResponse;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceAdjustmentSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineItemResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialDocumentAdjustmentService {

    private static final String ENTITY = "financialDocument";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final EnumSet<FinancialDocumentItemAdjustmentAction> CREDIT_ACTIONS =
            EnumSet.of(
                    FinancialDocumentItemAdjustmentAction.REMOVE,
                    FinancialDocumentItemAdjustmentAction.PARTIAL_CREDIT,
                    FinancialDocumentItemAdjustmentAction.REDUCE
            );

    private static final EnumSet<FinancialDocumentItemAdjustmentAction> DEBIT_ACTIONS =
            EnumSet.of(
                    FinancialDocumentItemAdjustmentAction.ADD,
                    FinancialDocumentItemAdjustmentAction.ADD_NEW,
                    FinancialDocumentItemAdjustmentAction.INCREASE
            );

    private final FinancialDocumentRepository documentRepo;
    private final FinancialDocumentItemRepository itemRepo;
    private final BillingChargeLineRepository chargeLineRepo;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientLedgerRepository ledgerRepository;
    private final FinancialDocumentStatusService statusService;
    private final FinancialDocumentBalanceService balanceService;
    private final PatientPaymentAllocationRepository allocationRepo;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final BillingEngineService billingEngineService;
    private final FinancialDocumentNumberAssignmentService documentNumberAssignmentService;

    @Transactional(readOnly = true)
    public List<InvoiceLineItemResponse> listInvoiceLineItems(Long invoiceId) {
        FinancialDocument invoice = loadInvoice(invoiceId);
        List<FinancialDocumentItem> items = itemRepo.findByDocument_Id(invoiceId);
        Map<Long, BillingChargeLine> chargeLinesByPsp = loadChargeLinesByPsp(invoice.getEncounterId());

        return items.stream()
                .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                .map(item -> toInvoiceLineItemResponse(item, chargeLinesByPsp))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AddableChargeLineResponse> listAddableChargeLines(Long invoiceId) {
        FinancialDocument invoice = loadInvoice(invoiceId);
        Set<Long> invoicedPspIds =
                itemRepo.findByDocument_Id(invoiceId).stream()
                        .map(FinancialDocumentItem::getPatientServiceProductId)
                        .collect(Collectors.toSet());

        return chargeLineRepo
                .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                        invoice.getEncounterId(),
                        EXCLUDED_LINE_STATUSES
                )
                .stream()
                .filter(line -> !invoicedPspIds.contains(line.getPatientServiceProduct().getId()))
                .filter(line -> shareForSubtype(line, invoice.getDocumentSubtype()).signum() > 0)
                .map(line -> new AddableChargeLineResponse(
                        line.getId(),
                        line.getPatientServiceProduct().getId(),
                        line.getItemCode(),
                        line.getItemDescription(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getNetAmount(),
                        money(line.getPatientResponsibilityAmount()),
                        money(line.getInsuranceResponsibilityAmount()),
                        line.getCurrency()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvoiceAdjustmentSummaryResponse getInvoiceAdjustmentSummary(Long invoiceId) {
        FinancialDocument invoice = loadInvoice(invoiceId);

        List<FinancialDocument> children = documentRepo.findAllByParentDocumentId(invoiceId);

        BigDecimal totalCreditNotes = sumByType(children, FinancialDocumentType.CREDIT_NOTE);
        BigDecimal totalDebitNotes = sumByType(children, FinancialDocumentType.DEBIT_NOTE);
        BigDecimal totalPaid = safe(allocationRepo.sumPaidByDocument(invoiceId));
        BigDecimal outstanding = balanceService.calculateOutstanding(invoiceId);

        List<FinancialDocumentAdjustmentResponse> adjustments = children.stream()
                .filter(child -> isAdjustmentType(child.getDocumentType()))
                .sorted(Comparator.comparing(
                        FinancialDocument::getCreatedDate,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toAdjustmentResponse)
                .toList();

        return new InvoiceAdjustmentSummaryResponse(
                invoice.getId(),
                invoice.getDocumentNumber(),
                invoice.getDocumentSubtype(),
                invoice.getStatus(),
                safe(invoice.getTotalAmount()),
                totalCreditNotes,
                totalDebitNotes,
                totalPaid,
                outstanding,
                invoice.getCurrency(),
                adjustments
        );
    }

    public FinancialDocumentAdjustmentResponse createCreditNote(
            Long invoiceId,
            CreateFinancialDocumentAdjustmentRequest request
    ) {
        return createLineAdjustment(
                invoiceId,
                request,
                FinancialDocumentType.CREDIT_NOTE,
                CREDIT_ACTIONS
        );
    }

    public FinancialDocumentAdjustmentResponse createDebitNote(
            Long invoiceId,
            CreateFinancialDocumentAdjustmentRequest request
    ) {
        return createLineAdjustment(
                invoiceId,
                request,
                FinancialDocumentType.DEBIT_NOTE,
                DEBIT_ACTIONS
        );
    }

    public void createRefund(Long invoiceId, BigDecimal amount) {
        FinancialDocument invoice = loadInvoice(invoiceId);

        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new BadRequestAlertException(
                    "Invalid refund amount",
                    ENTITY,
                    "adjustment.refund.invalidAmount"
            );
        }

        BigDecimal balance = balanceService.calculateOutstanding(invoiceId);

        if (balance.compareTo(ZERO) >= 0) {
            throw new BadRequestAlertException(
                    "No overpayment — refund is not allowed",
                    ENTITY,
                    "adjustment.refund.noOverpayment"
            );
        }

        BigDecimal overpayment = balance.abs();

        if (amount.compareTo(overpayment) > 0) {
            throw new BadRequestAlertException(
                    "Refund exceeds overpayment",
                    ENTITY,
                    "adjustment.refund.exceedsOverpayment"
            );
        }

        applyRefundLedger(invoice, amount);
    }

    private FinancialDocumentAdjustmentResponse createLineAdjustment(
            Long invoiceId,
            CreateFinancialDocumentAdjustmentRequest request,
            FinancialDocumentType documentType,
            EnumSet<FinancialDocumentItemAdjustmentAction> allowedActions
    ) {
        FinancialDocument invoice = loadInvoice(invoiceId);
        validateIssuedInvoice(invoice);

        String reason = normalizeReason(request.reason());
        validateAdjustmentLines(request.lines(), allowedActions);

        List<FinancialDocumentItem> invoiceItems = itemRepo.findByDocument_Id(invoiceId);
        Map<Long, FinancialDocumentItem> invoiceItemsById =
                invoiceItems.stream()
                        .collect(Collectors.toMap(FinancialDocumentItem::getId, Function.identity()));

        List<PreparedAdjustmentLine> preparedLines = new ArrayList<>();
        for (InvoiceLineAdjustmentRequest lineRequest : request.lines()) {
            preparedLines.add(
                    prepareLine(invoice, lineRequest, invoiceItemsById, allowedActions)
            );
        }

        BigDecimal totalAmount = preparedLines.stream()
                .map(PreparedAdjustmentLine::amount)
                .reduce(ZERO, BigDecimal::add);

        if (totalAmount.compareTo(ZERO) <= 0) {
            throw new BadRequestAlertException(
                    "Adjustment total must be greater than zero",
                    ENTITY,
                    "adjustment.invalidAmount"
            );
        }

        if (documentType == FinancialDocumentType.CREDIT_NOTE) {
            BigDecimal outstanding = balanceService.calculateOutstanding(invoiceId);
            if (outstanding.compareTo(ZERO) <= 0) {
                throw new BadRequestAlertException(
                        "Invoice has no outstanding balance to credit",
                        ENTITY,
                        "adjustment.credit.noOutstanding"
                );
            }
            if (totalAmount.compareTo(outstanding) > 0) {
                throw new BadRequestAlertException(
                        "Credit amount exceeds outstanding balance",
                        ENTITY,
                        "adjustment.credit.exceedsOutstanding"
                );
            }
        }

        FinancialDocument adjustmentDocument = documentRepo.save(
                buildAdjustmentDocument(invoice, documentType, totalAmount, reason)
        );

        List<FinancialDocumentItem> savedItems = new ArrayList<>();
        for (PreparedAdjustmentLine preparedLine : preparedLines) {
            FinancialDocumentItem item = buildAdjustmentItem(
                    adjustmentDocument,
                    invoice,
                    preparedLine
            );
            savedItems.add(itemRepo.save(item));

            if (preparedLine.originalItem() != null && documentType == FinancialDocumentType.CREDIT_NOTE) {
                applyCreditToOriginalItem(preparedLine.originalItem(), preparedLine.amount());
            }
        }

        if (documentType == FinancialDocumentType.CREDIT_NOTE) {
            applyCreditNoteLedger(adjustmentDocument);
        } else {
            applyDebitNoteLedger(adjustmentDocument);
        }

        updateInvoiceStatus(invoiceId);

        return toAdjustmentResponse(adjustmentDocument);
    }

    private PreparedAdjustmentLine prepareLine(
            FinancialDocument invoice,
            InvoiceLineAdjustmentRequest lineRequest,
            Map<Long, FinancialDocumentItem> invoiceItemsById,
            EnumSet<FinancialDocumentItemAdjustmentAction> allowedActions
    ) {
        FinancialDocumentItemAdjustmentAction action = lineRequest.action();
        if (!allowedActions.contains(action)) {
            throw new BadRequestAlertException(
                    "Action " + action + " is not allowed for this adjustment type",
                    ENTITY,
                    "adjustment.invalidAction"
            );
        }

        return switch (action) {
            case REMOVE -> prepareRemoveLine(lineRequest, invoiceItemsById);
            case PARTIAL_CREDIT -> preparePartialCreditLine(lineRequest, invoiceItemsById);
            case REDUCE -> prepareReduceLine(lineRequest, invoiceItemsById);
            case ADD -> prepareAddLine(invoice, lineRequest);
            case ADD_NEW -> prepareAddNewLine(invoice, lineRequest);
            case INCREASE -> prepareIncreaseLine(lineRequest, invoiceItemsById);
        };
    }

    private PreparedAdjustmentLine prepareRemoveLine(
            InvoiceLineAdjustmentRequest request,
            Map<Long, FinancialDocumentItem> invoiceItemsById
    ) {
        FinancialDocumentItem original = requireInvoiceItem(request.documentItemId(), invoiceItemsById);
        BigDecimal creditAmount = money(original.getRemainingAmount());
        if (creditAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Invoice line has no remaining balance to remove",
                    ENTITY,
                    "adjustment.line.noRemaining"
            );
        }

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.REMOVE,
                original,
                null,
                creditAmount,
                original.getQuantity(),
                original.getUnitPrice(),
                resolveItemCode(original),
                resolveItemDescription(original)
        );
    }

    private PreparedAdjustmentLine preparePartialCreditLine(
            InvoiceLineAdjustmentRequest request,
            Map<Long, FinancialDocumentItem> invoiceItemsById
    ) {
        FinancialDocumentItem original = requireInvoiceItem(request.documentItemId(), invoiceItemsById);
        BigDecimal creditAmount = normalizeAmount(request.amount());
        BigDecimal remaining = money(original.getRemainingAmount());

        if (creditAmount.compareTo(remaining) > 0) {
            throw new BadRequestAlertException(
                    "Partial credit exceeds the line remaining amount",
                    ENTITY,
                    "adjustment.line.creditExceedsRemaining"
            );
        }

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.PARTIAL_CREDIT,
                original,
                null,
                creditAmount,
                original.getQuantity(),
                original.getUnitPrice(),
                resolveItemCode(original),
                resolveItemDescription(original)
        );
    }

    private PreparedAdjustmentLine prepareReduceLine(
            InvoiceLineAdjustmentRequest request,
            Map<Long, FinancialDocumentItem> invoiceItemsById
    ) {
        FinancialDocumentItem original = requireInvoiceItem(request.documentItemId(), invoiceItemsById);
        BigDecimal newQuantity = requireQuantity(request.quantity());
        BigDecimal newUnitPrice = requireUnitPrice(request.unitPrice());
        BigDecimal newNet = money(newQuantity.multiply(newUnitPrice));
        BigDecimal oldNet = money(original.getNetAmount());
        BigDecimal remaining = money(original.getRemainingAmount());

        if (newNet.compareTo(oldNet) >= 0) {
            throw new BadRequestAlertException(
                    "Reduced line amount must be lower than the current line amount",
                    ENTITY,
                    "adjustment.line.reduceNotLower"
            );
        }

        BigDecimal creditAmount = oldNet.subtract(newNet);
        if (creditAmount.compareTo(remaining) > 0) {
            creditAmount = remaining;
        }

        if (creditAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "No credit amount remains available for this line update",
                    ENTITY,
                    "adjustment.line.noRemaining"
            );
        }

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.REDUCE,
                original,
                null,
                creditAmount,
                newQuantity.setScale(0, RoundingMode.HALF_UP).longValue(),
                newUnitPrice,
                resolveItemCode(original),
                resolveItemDescription(original)
        );
    }

    private PreparedAdjustmentLine prepareAddLine(
            FinancialDocument invoice,
            InvoiceLineAdjustmentRequest request
    ) {
        if (request.chargeLineId() == null) {
            throw new BadRequestAlertException(
                    "Charge line is required when adding a service",
                    ENTITY,
                    "adjustment.line.chargeLineRequired"
            );
        }

        BillingChargeLine chargeLine = chargeLineRepo.findById(request.chargeLineId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Charge line not found",
                        ENTITY,
                        "adjustment.chargeLine.notFound"
                ));

        if (!invoice.getEncounterId().equals(chargeLine.getEncounter().getId())) {
            throw new BadRequestAlertException(
                    "Charge line does not belong to the invoice encounter",
                    ENTITY,
                    "adjustment.chargeLine.encounterMismatch"
            );
        }

        if (EXCLUDED_LINE_STATUSES.contains(chargeLine.getStatus())) {
            throw new BadRequestAlertException(
                    "Charge line is not active",
                    ENTITY,
                    "adjustment.chargeLine.inactive"
            );
        }

        Long pspId = chargeLine.getPatientServiceProduct().getId();
        boolean alreadyInvoiced = itemRepo.findByDocument_Id(invoice.getId()).stream()
                .anyMatch(item -> pspId.equals(item.getPatientServiceProductId()));

        if (alreadyInvoiced) {
            throw new BadRequestAlertException(
                    "Service is already included on this invoice",
                    ENTITY,
                    "adjustment.line.alreadyInvoiced"
            );
        }

        BigDecimal debitAmount = shareForSubtype(chargeLine, invoice.getDocumentSubtype());
        if (debitAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Selected service has no billable amount for this invoice type",
                    ENTITY,
                    "adjustment.line.noAmount"
            );
        }

        BigDecimal lineNet = money(chargeLine.getNetAmount());
        BigDecimal grossAmount = proportional(chargeLine.getGrossAmount(), debitAmount, lineNet);
        BigDecimal discountAmount = proportional(chargeLine.getDiscountAmount(), debitAmount, lineNet);
        BigDecimal taxAmount = proportional(chargeLine.getTaxAmount(), debitAmount, lineNet);

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.ADD,
                null,
                chargeLine,
                debitAmount,
                chargeLine.getQuantity().setScale(0, RoundingMode.HALF_UP).longValue(),
                debitAmount.divide(chargeLine.getQuantity(), 4, RoundingMode.HALF_UP),
                chargeLine.getItemCode(),
                chargeLine.getItemDescription(),
                grossAmount,
                discountAmount,
                taxAmount
        );
    }

    private PreparedAdjustmentLine prepareAddNewLine(
            FinancialDocument invoice,
            InvoiceLineAdjustmentRequest request
    ) {
        if (request.billingItemType() == null) {
            throw new BadRequestAlertException(
                    "Service category is required when adding a new service",
                    ENTITY,
                    "adjustment.service.categoryRequired"
            );
        }

        BigDecimal quantity = requireQuantity(request.quantity());
        BigDecimal unitPrice = requireUnitPrice(request.unitPrice());
        Currency currency = request.currency() != null ? request.currency() : invoice.getCurrency();
        if (currency == null) {
            throw new BadRequestAlertException(
                    "Currency is required when adding a new service",
                    ENTITY,
                    "adjustment.service.currencyRequired"
            );
        }

        validateNewServiceReference(request);

        PatientEncounter encounter = patientEncounterRepository.findById(invoice.getEncounterId())
                .orElseThrow(() -> new NotFoundAlertException(
                        "Encounter not found",
                        ENTITY,
                        "encounter.notFound"
                ));

        Long facilityId = encounter.getFacilityId();
        if (facilityId == null) {
            throw new BadRequestAlertException(
                    "Encounter facility is required to bill a new service",
                    ENTITY,
                    "encounter.facility.required"
            );
        }

        PatientServiceProductCreateDTO createDto = new PatientServiceProductCreateDTO(
                invoice.getPatientId(),
                invoice.getEncounterId(),
                request.billingItemType(),
                request.brandMedicationId(),
                request.diagnosticTestId(),
                request.serviceId(),
                request.procedureId(),
                quantity.setScale(0, RoundingMode.HALF_UP).longValue(),
                unitPrice,
                currency,
                request.serviceSource() != null
                        ? request.serviceSource()
                        : ServiceSource.SERVICE_AND_PRODUCT,
                request.sourceId(),
                request.notes()
        );

        var createdService = patientServiceAndProductService.create(createDto);

        billingEngineService.onManualBilling(
                createdService.getId(),
                facilityId,
                "debit-note-add-new-" + createdService.getId()
        );

        BillingChargeLine chargeLine = chargeLineRepo
                .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                        createdService.getId(),
                        EXCLUDED_LINE_STATUSES
                )
                .orElseThrow(() -> new BadRequestAlertException(
                        "Billing charge line was not created for the new service",
                        ENTITY,
                        "adjustment.chargeLine.notCreated"
                ));

        BigDecimal debitAmount = shareForSubtype(chargeLine, invoice.getDocumentSubtype());
        if (debitAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Selected service has no billable amount for this invoice type",
                    ENTITY,
                    "adjustment.line.noAmount"
            );
        }

        BigDecimal lineNet = money(chargeLine.getNetAmount());
        BigDecimal grossAmount = proportional(chargeLine.getGrossAmount(), debitAmount, lineNet);
        BigDecimal discountAmount = proportional(chargeLine.getDiscountAmount(), debitAmount, lineNet);
        BigDecimal taxAmount = proportional(chargeLine.getTaxAmount(), debitAmount, lineNet);

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.ADD_NEW,
                null,
                chargeLine,
                debitAmount,
                chargeLine.getQuantity().setScale(0, RoundingMode.HALF_UP).longValue(),
                debitAmount.divide(chargeLine.getQuantity(), 4, RoundingMode.HALF_UP),
                chargeLine.getItemCode(),
                chargeLine.getItemDescription(),
                grossAmount,
                discountAmount,
                taxAmount
        );
    }

    private void validateNewServiceReference(InvoiceLineAdjustmentRequest request) {
        BillingItemTypes type = request.billingItemType();
        switch (type) {
            case MEDICATION -> {
                if (request.brandMedicationId() == null) {
                    throw new BadRequestAlertException(
                            "Medication is required",
                            ENTITY,
                            "adjustment.service.medicationRequired"
                    );
                }
            }
            case LABORATORY, RADIOLOGY, PATHOLOGY -> {
                if (request.diagnosticTestId() == null) {
                    throw new BadRequestAlertException(
                            "Diagnostic test is required",
                            ENTITY,
                            "adjustment.service.diagnosticRequired"
                    );
                }
            }
            case SERVICE -> {
                if (request.serviceId() == null) {
                    throw new BadRequestAlertException(
                            "Service is required",
                            ENTITY,
                            "adjustment.service.serviceRequired"
                    );
                }
            }
            case PROCEDURE -> {
                if (request.procedureId() == null) {
                    throw new BadRequestAlertException(
                            "Procedure is required",
                            ENTITY,
                            "adjustment.service.procedureRequired"
                    );
                }
            }
            default -> throw new BadRequestAlertException(
                    "Unsupported service category for debit note",
                    ENTITY,
                    "adjustment.service.unsupportedCategory"
            );
        }
    }

    private PreparedAdjustmentLine prepareIncreaseLine(
            InvoiceLineAdjustmentRequest request,
            Map<Long, FinancialDocumentItem> invoiceItemsById
    ) {
        FinancialDocumentItem original = requireInvoiceItem(request.documentItemId(), invoiceItemsById);
        BigDecimal newQuantity = requireQuantity(request.quantity());
        BigDecimal newUnitPrice = requireUnitPrice(request.unitPrice());
        BigDecimal newNet = money(newQuantity.multiply(newUnitPrice));
        BigDecimal oldNet = money(original.getNetAmount());

        if (newNet.compareTo(oldNet) <= 0) {
            throw new BadRequestAlertException(
                    "Increased line amount must be higher than the current line amount",
                    ENTITY,
                    "adjustment.line.increaseNotHigher"
            );
        }

        BigDecimal debitAmount = newNet.subtract(oldNet);

        return new PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction.INCREASE,
                original,
                null,
                debitAmount,
                newQuantity.setScale(0, RoundingMode.HALF_UP).longValue(),
                newUnitPrice,
                resolveItemCode(original),
                resolveItemDescription(original)
        );
    }

    private FinancialDocumentItem buildAdjustmentItem(
            FinancialDocument document,
            FinancialDocument invoice,
            PreparedAdjustmentLine preparedLine
    ) {
        FinancialDocumentSubtype subtype = invoice.getDocumentSubtype();
        BigDecimal amount = preparedLine.amount();
        BigDecimal grossAmount = preparedLine.grossAmount() != null
                ? preparedLine.grossAmount()
                : amount;
        BigDecimal discountAmount = preparedLine.discountAmount() != null
                ? preparedLine.discountAmount()
                : ZERO;
        BigDecimal taxAmount = preparedLine.taxAmount() != null
                ? preparedLine.taxAmount()
                : ZERO;

        BigDecimal patientShare =
                subtype == FinancialDocumentSubtype.PATIENT ? amount : ZERO;
        BigDecimal insuranceShare =
                subtype == FinancialDocumentSubtype.INSURANCE_CLAIM ? amount : ZERO;

        Long pspId = preparedLine.originalItem() != null
                ? preparedLine.originalItem().getPatientServiceProductId()
                : preparedLine.chargeLine().getPatientServiceProduct().getId();

        Long chargeLineId = preparedLine.chargeLine() != null
                ? preparedLine.chargeLine().getId()
                : preparedLine.originalItem() != null
                        ? preparedLine.originalItem().getBillingChargeLineId()
                        : null;

        return FinancialDocumentItem.builder()
                .document(document)
                .patientServiceProductId(pspId)
                .billingChargeLineId(chargeLineId)
                .parentDocumentItemId(
                        preparedLine.originalItem() != null
                                ? preparedLine.originalItem().getId()
                                : null
                )
                .itemCode(preparedLine.itemCode())
                .itemDescription(preparedLine.itemDescription())
                .adjustmentAction(preparedLine.action())
                .quantity(preparedLine.quantity())
                .unitPrice(preparedLine.unitPrice())
                .grossAmount(grossAmount)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .netAmount(amount)
                .patientShareAmount(patientShare)
                .insuranceShareAmount(insuranceShare)
                .paidAmount(ZERO)
                .remainingAmount(amount)
                .insurancePaidAmount(ZERO)
                .insuranceRemainingAmount(insuranceShare)
                .status(FinancialDocumentItemStatus.PENDING)
                .currency(document.getCurrency())
                .build();
    }

    private void applyCreditToOriginalItem(
            FinancialDocumentItem originalItem,
            BigDecimal creditAmount
    ) {
        BigDecimal remaining = money(originalItem.getRemainingAmount()).subtract(creditAmount);
        if (remaining.signum() < 0) {
            remaining = ZERO;
        }

        originalItem.setRemainingAmount(remaining);

        if (remaining.signum() == 0) {
            originalItem.setStatus(FinancialDocumentItemStatus.PAID);
        } else if (money(originalItem.getPaidAmount()).signum() > 0) {
            originalItem.setStatus(FinancialDocumentItemStatus.PARTIALLY_PAID);
        } else {
            originalItem.setStatus(FinancialDocumentItemStatus.PENDING);
        }

        itemRepo.save(originalItem);
    }

    private FinancialDocumentItem requireInvoiceItem(
            Long documentItemId,
            Map<Long, FinancialDocumentItem> invoiceItemsById
    ) {
        if (documentItemId == null) {
            throw new BadRequestAlertException(
                    "Invoice line is required",
                    ENTITY,
                    "adjustment.line.required"
            );
        }

        FinancialDocumentItem item = invoiceItemsById.get(documentItemId);
        if (item == null) {
            throw new NotFoundAlertException(
                    "Invoice line not found",
                    ENTITY,
                    "adjustment.line.notFound"
            );
        }

        return item;
    }

    private void validateAdjustmentLines(
            List<InvoiceLineAdjustmentRequest> lines,
            EnumSet<FinancialDocumentItemAdjustmentAction> allowedActions
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new BadRequestAlertException(
                    "At least one line adjustment is required",
                    ENTITY,
                    "adjustment.lines.required"
            );
        }

        Set<Long> touchedItems = new HashSet<>();
        Set<Long> touchedChargeLines = new HashSet<>();

        for (InvoiceLineAdjustmentRequest line : lines) {
            if (line.action() == null || !allowedActions.contains(line.action())) {
                throw new BadRequestAlertException(
                        "Invalid adjustment action for this document type",
                        ENTITY,
                        "adjustment.invalidAction"
                );
            }

            if (line.documentItemId() != null && !touchedItems.add(line.documentItemId())) {
                throw new BadRequestAlertException(
                        "Duplicate invoice line in the same adjustment",
                        ENTITY,
                        "adjustment.line.duplicate"
                );
            }

            if (line.chargeLineId() != null && !touchedChargeLines.add(line.chargeLineId())) {
                throw new BadRequestAlertException(
                        "Duplicate charge line in the same adjustment",
                        ENTITY,
                        "adjustment.line.duplicate"
                );
            }
        }
    }

    private Map<Long, BillingChargeLine> loadChargeLinesByPsp(Long encounterId) {
        return chargeLineRepo
                .findAllByEncounter_IdAndStatusNotInOrderByIdAsc(
                        encounterId,
                        EXCLUDED_LINE_STATUSES
                )
                .stream()
                .collect(Collectors.toMap(
                        line -> line.getPatientServiceProduct().getId(),
                        Function.identity(),
                        (left, right) -> right
                ));
    }

    private InvoiceLineItemResponse toInvoiceLineItemResponse(
            FinancialDocumentItem item,
            Map<Long, BillingChargeLine> chargeLinesByPsp
    ) {
        BillingChargeLine chargeLine = chargeLinesByPsp.get(item.getPatientServiceProductId());

        return new InvoiceLineItemResponse(
                item.getId(),
                item.getPatientServiceProductId(),
                chargeLine != null ? chargeLine.getId() : item.getBillingChargeLineId(),
                firstNonBlank(item.getItemCode(), chargeLine != null ? chargeLine.getItemCode() : null),
                firstNonBlank(
                        item.getItemDescription(),
                        chargeLine != null ? chargeLine.getItemDescription() : null
                ),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getGrossAmount(),
                item.getDiscountAmount(),
                item.getTaxAmount(),
                item.getNetAmount(),
                item.getPaidAmount(),
                item.getRemainingAmount(),
                item.getStatus() != null ? item.getStatus().name() : null,
                item.getCurrency()
        );
    }

    private FinancialDocument loadInvoice(Long invoiceId) {
        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Invoice not found",
                        ENTITY,
                        "invoice.notFound"
                ));

        if (invoice.getDocumentType() != FinancialDocumentType.INVOICE) {
            throw new BadRequestAlertException(
                    "Adjustments can only be applied to invoices",
                    ENTITY,
                    "adjustment.parentNotInvoice"
            );
        }

        return invoice;
    }

    private void validateIssuedInvoice(FinancialDocument invoice) {
        if (invoice.getStatus() == FinancialDocumentStatus.DRAFT) {
            throw new BadRequestAlertException(
                    "Cannot adjust a draft invoice",
                    ENTITY,
                    "adjustment.invoiceDraft"
            );
        }

        if (invoice.getStatus() == FinancialDocumentStatus.CANCELLED) {
            throw new BadRequestAlertException(
                    "Cannot adjust a cancelled invoice",
                    ENTITY,
                    "adjustment.invoiceCancelled"
            );
        }
    }

    private FinancialDocument buildAdjustmentDocument(
            FinancialDocument invoice,
            FinancialDocumentType documentType,
            BigDecimal amount,
            String reason
    ) {
        FinancialDocument document = FinancialDocument.builder()
                .documentType(documentType)
                .documentSubtype(invoice.getDocumentSubtype())
                .parentDocumentId(invoice.getId())
                .patientId(invoice.getPatientId())
                .encounterId(invoice.getEncounterId())
                .totalAmount(amount)
                .currency(invoice.getCurrency())
                .adjustmentReason(reason)
                .status(FinancialDocumentStatus.ISSUED)
                .createdDate(Instant.now())
                .build();

        assignConfiguredDocumentNumber(document);
        return document;
    }

    private void assignConfiguredDocumentNumber(FinancialDocument document) {
        patientEncounterRepository.findById(document.getEncounterId())
                .map(PatientEncounter::getFacilityId)
                .flatMap(facilityId ->
                        documentNumberAssignmentService.assignNextDocumentNumber(
                                facilityId,
                                document.getDocumentType(),
                                LocalDate.now()
                        )
                )
                .ifPresent(document::setDocumentNumber);
    }

    private BigDecimal shareForSubtype(
            BillingChargeLine line,
            FinancialDocumentSubtype subtype
    ) {
        return subtype == FinancialDocumentSubtype.INSURANCE_CLAIM
                ? money(line.getInsuranceResponsibilityAmount())
                : money(line.getPatientResponsibilityAmount());
    }

    private BigDecimal proportional(
            BigDecimal amount,
            BigDecimal share,
            BigDecimal whole
    ) {
        if (amount == null || share == null || whole == null || whole.signum() == 0) {
            return ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        return money(
                amount.multiply(share)
                        .divide(whole, 4, RoundingMode.HALF_UP)
        );
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(ZERO) <= 0) {
            throw new BadRequestAlertException(
                    "Adjustment amount must be greater than zero",
                    ENTITY,
                    "adjustment.invalidAmount"
            );
        }
        return money(amount);
    }

    private BigDecimal requireQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(ZERO) <= 0) {
            throw new BadRequestAlertException(
                    "Quantity must be greater than zero",
                    ENTITY,
                    "adjustment.line.invalidQuantity"
            );
        }
        return quantity;
    }

    private BigDecimal requireUnitPrice(BigDecimal unitPrice) {
        if (unitPrice == null || unitPrice.compareTo(ZERO) <= 0) {
            throw new BadRequestAlertException(
                    "Unit price must be greater than zero",
                    ENTITY,
                    "adjustment.line.invalidUnitPrice"
            );
        }
        return money(unitPrice);
    }

    private String normalizeReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        return reason.trim();
    }

    private String resolveItemCode(FinancialDocumentItem item) {
        if (StringUtils.hasText(item.getItemCode())) {
            return item.getItemCode();
        }
        return null;
    }

    private String resolveItemDescription(FinancialDocumentItem item) {
        if (StringUtils.hasText(item.getItemDescription())) {
            return item.getItemDescription();
        }
        return "Invoice line #" + item.getId();
    }

    private String firstNonBlank(String primary, String fallback) {
        if (StringUtils.hasText(primary)) {
            return primary;
        }
        return StringUtils.hasText(fallback) ? fallback : null;
    }

    private void applyCreditNoteLedger(FinancialDocument creditNote) {
        postAdjustmentLedger(creditNote, LedgerSource.CREDIT_NOTE, true);
    }

    private void applyDebitNoteLedger(FinancialDocument debitNote) {
        postAdjustmentLedger(debitNote, LedgerSource.DEBIT_NOTE, false);
    }

    private void postAdjustmentLedger(
            FinancialDocument document,
            LedgerSource source,
            boolean creditNote
    ) {
        Long patientId = document.getPatientId();
        BigDecimal amount = document.getTotalAmount();

        if (creditNote) {
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(patientId)
                            .type(LedgerEntryType.DEBIT)
                            .account(LedgerAccount.REVENUE)
                            .source(source)
                            .referenceId(document.getId())
                            .amount(amount)
                            .currency(document.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );
            ledgerRepository.save(
                    PatientLedgerEntry.builder()
                            .patientId(patientId)
                            .type(LedgerEntryType.CREDIT)
                            .account(LedgerAccount.PATIENT_RECEIVABLE)
                            .source(source)
                            .referenceId(document.getId())
                            .amount(amount)
                            .currency(document.getCurrency())
                            .createdDate(Instant.now())
                            .build()
            );
            return;
        }

        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.DEBIT)
                        .account(LedgerAccount.PATIENT_RECEIVABLE)
                        .source(source)
                        .referenceId(document.getId())
                        .amount(amount)
                        .currency(document.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.CREDIT)
                        .account(LedgerAccount.REVENUE)
                        .source(source)
                        .referenceId(document.getId())
                        .amount(amount)
                        .currency(document.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
    }

    private void applyRefundLedger(FinancialDocument invoice, BigDecimal amount) {
        Long patientId = invoice.getPatientId();

        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.DEBIT)
                        .account(LedgerAccount.PATIENT_RECEIVABLE)
                        .source(LedgerSource.REFUND)
                        .referenceId(invoice.getId())
                        .amount(amount)
                        .currency(invoice.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );

        ledgerRepository.save(
                PatientLedgerEntry.builder()
                        .patientId(patientId)
                        .type(LedgerEntryType.CREDIT)
                        .account(LedgerAccount.CASH)
                        .source(LedgerSource.REFUND)
                        .referenceId(invoice.getId())
                        .amount(amount)
                        .currency(invoice.getCurrency())
                        .createdDate(Instant.now())
                        .build()
        );
    }

    private void updateInvoiceStatus(Long invoiceId) {
        FinancialDocument invoice = documentRepo.findById(invoiceId)
                .orElseThrow(() -> new NotFoundAlertException(
                        "Invoice not found",
                        ENTITY,
                        "invoice.notFound"
                ));

        invoice.setStatus(statusService.calculate(invoiceId));
        documentRepo.save(invoice);
    }

    private FinancialDocumentAdjustmentResponse toAdjustmentResponse(FinancialDocument document) {
        List<FinancialDocumentAdjustmentItemResponse> items =
                itemRepo.findByDocument_Id(document.getId()).stream()
                        .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                        .map(item -> new FinancialDocumentAdjustmentItemResponse(
                                item.getId(),
                                item.getAdjustmentAction(),
                                item.getParentDocumentItemId(),
                                item.getBillingChargeLineId(),
                                item.getItemCode(),
                                item.getItemDescription(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getNetAmount(),
                                item.getCurrency()
                        ))
                        .toList();

        return new FinancialDocumentAdjustmentResponse(
                document.getId(),
                document.getDocumentNumber(),
                document.getDocumentType(),
                document.getDocumentSubtype(),
                document.getStatus(),
                document.getParentDocumentId(),
                document.getTotalAmount(),
                document.getCurrency(),
                document.getAdjustmentReason(),
                document.getCreatedDate(),
                items
        );
    }

    private boolean isAdjustmentType(FinancialDocumentType type) {
        return type == FinancialDocumentType.CREDIT_NOTE
                || type == FinancialDocumentType.DEBIT_NOTE;
    }

    private BigDecimal sumByType(List<FinancialDocument> documents, FinancialDocumentType type) {
        return documents.stream()
                .filter(document -> document.getDocumentType() == type)
                .map(document -> safe(document.getTotalAmount()))
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            return ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private record PreparedAdjustmentLine(
            FinancialDocumentItemAdjustmentAction action,
            FinancialDocumentItem originalItem,
            BillingChargeLine chargeLine,
            BigDecimal amount,
            Long quantity,
            BigDecimal unitPrice,
            String itemCode,
            String itemDescription,
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal taxAmount
    ) {
        private PreparedAdjustmentLine(
                FinancialDocumentItemAdjustmentAction action,
                FinancialDocumentItem originalItem,
                BillingChargeLine chargeLine,
                BigDecimal amount,
                Long quantity,
                BigDecimal unitPrice,
                String itemCode,
                String itemDescription
        ) {
            this(
                    action,
                    originalItem,
                    chargeLine,
                    amount,
                    quantity,
                    unitPrice,
                    itemCode,
                    itemDescription,
                    amount,
                    ZERO,
                    ZERO
            );
        }
    }
}
