package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingAllocation;
import com.dazzle.asklepios.domain.BillingCharge;
import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.BillingWallet;
import com.dazzle.asklepios.domain.FinancialDocument;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.FinancialDocumentItemStatus;
import com.dazzle.asklepios.domain.PatientLedgerEntry;
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
import com.dazzle.asklepios.domain.enumeration.billing.AllocationSourceType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingAllocationStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryCategory;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerEntryDirection;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerScope;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerSourceChannel;
import com.dazzle.asklepios.domain.enumeration.billing.BillingLedgerTransactionType;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.repository.BillingAllocationRepository;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeRepository;
import com.dazzle.asklepios.repository.BillingLedgerRepository;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.FinancialDocumentRepository;
import com.dazzle.asklepios.repository.PatientLedgerRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import com.dazzle.asklepios.security.SecurityUtils;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.service.dto.billing.BillingLedgerEntryRequest;
import com.dazzle.asklepios.service.dto.billing.AddableChargeLineResponse;
import com.dazzle.asklepios.service.dto.billing.CreateFinancialDocumentAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentItemResponse;
import com.dazzle.asklepios.service.dto.billing.FinancialDocumentAdjustmentResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceAdjustmentSummaryResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineAdjustmentRequest;
import com.dazzle.asklepios.service.dto.billing.InvoiceLineItemResponse;
import com.dazzle.asklepios.service.dto.billing.InvoiceItemPricingAdjustmentSnapshot;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FinancialDocumentAdjustmentService {

    private static final Logger LOG =
            LoggerFactory.getLogger(FinancialDocumentAdjustmentService.class);

    private static final String ENTITY = "financialDocument";
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final EnumSet<BillingChargeLineStatus> EXCLUDED_LINE_STATUSES =
            EnumSet.of(
                    BillingChargeLineStatus.CANCELLED,
                    BillingChargeLineStatus.REVERSED
            );

    private static final EnumSet<BillingChargeStatus> EXCLUDED_CHARGE_STATUSES =
            EnumSet.of(
                    BillingChargeStatus.CANCELLED,
                    BillingChargeStatus.REVERSED
            );

    private static final EnumSet<BillingAllocationStatus> ACTIVE_ALLOCATION_STATUSES =
            EnumSet.of(
                    BillingAllocationStatus.ACTIVE,
                    BillingAllocationStatus.PARTIALLY_REVERSED
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
    private final BillingChargeRepository billingChargeRepository;
    private final BillingAllocationRepository billingAllocationRepository;
    private final PatientEncounterRepository patientEncounterRepository;
    private final PatientLedgerRepository ledgerRepository;
    private final FinancialDocumentStatusService statusService;
    private final FinancialDocumentBalanceService balanceService;
    private final PatientPaymentAllocationRepository allocationRepo;
    private final PatientServiceAndProductService patientServiceAndProductService;
    private final BillingChargeService billingChargeService;
    private final FinancialDocumentNumberAssignmentService documentNumberAssignmentService;
    private final BillingWalletService billingWalletService;
    private final BillingLedgerService billingLedgerService;
    private final BillingLedgerRepository billingLedgerRepository;
    private final BillingDebitService billingDebitService;
    private final InvoiceItemPricingSnapshotService invoiceItemPricingSnapshotService;
    private final InvoiceApplicableOnAdjustmentService invoiceApplicableOnAdjustmentService;

    @Transactional(readOnly = true)
    public List<InvoiceLineItemResponse> listInvoiceLineItems(Long invoiceId) {
        FinancialDocument invoice = loadInvoice(invoiceId);
        Map<Long, BillingChargeLine> chargeLinesByPsp = loadChargeLinesByPsp(invoice.getEncounterId());

        List<InvoiceLineItemResponse> responses = new ArrayList<>();
        itemRepo.findByDocument_Id(invoiceId).stream()
                .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                .forEach(item ->
                        responses.add(
                                toInvoiceLineItemResponse(item, chargeLinesByPsp, "INVOICE")
                        )
                );

        documentRepo.findAllByParentDocumentId(invoiceId).stream()
                .filter(child -> child.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)
                .forEach(debitNote ->
                        itemRepo.findByDocument_Id(debitNote.getId()).stream()
                                .sorted(Comparator.comparing(FinancialDocumentItem::getId))
                                .forEach(item ->
                                        responses.add(
                                                toInvoiceLineItemResponse(
                                                        item,
                                                        chargeLinesByPsp,
                                                        "DEBIT_NOTE"
                                                )
                                        )
                                )
                );

        return responses;
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
                        money(line.getGrossAmount()),
                        money(line.getDiscountAmount()),
                        money(line.getTaxAmount()),
                        line.getCurrency()
                ))
                .toList();
    }

    public InvoiceAdjustmentSummaryResponse getInvoiceAdjustmentSummary(Long invoiceId) {
        reconcileMissingCreditNoteFinancialAdjustments(invoiceId);
        reconcileCreditNoteChargeLineSync(invoiceId);

        FinancialDocument invoice = loadInvoice(invoiceId);

        List<FinancialDocument> children = documentRepo.findAllByParentDocumentId(invoiceId);

        BigDecimal totalCreditNotes = sumByType(children, FinancialDocumentType.CREDIT_NOTE);
        BigDecimal totalDebitNotes = sumByType(children, FinancialDocumentType.DEBIT_NOTE);
        BigDecimal invoiceItemPaid =
                itemRepo.findByDocument_Id(invoiceId)
                        .stream()
                        .map(FinancialDocumentItem::getPaidAmount)
                        .map(this::safe)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal debitNoteItemPaid =
                children.stream()
                        .filter(child -> child.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)
                        .flatMap(child -> itemRepo.findByDocument_Id(child.getId()).stream())
                        .map(FinancialDocumentItem::getPaidAmount)
                        .map(this::safe)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaid =
                invoiceItemPaid
                        .add(debitNoteItemPaid)
                        .max(safe(allocationRepo.sumPaidByDocument(invoiceId)));
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
                outstanding.signum() > 0 || hasCreditableInvoiceLines(invoiceId),
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

        Map<Long, FinancialDocumentItem> invoiceItemsById = loadCreditableItemsById(invoiceId);

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
            BigDecimal outstandingReduction =
                    calculateCreditOutstandingReduction(preparedLines);

            if (outstanding.compareTo(ZERO) <= 0
                    && outstandingReduction.signum() <= 0
                    && !hasCreditableAdjustmentLines(preparedLines)) {
                throw new BadRequestAlertException(
                        "Invoice has no outstanding balance to credit",
                        ENTITY,
                        "adjustment.credit.noOutstanding"
                );
            }

            if (outstandingReduction.signum() > 0
                    && outstandingReduction.compareTo(outstanding) > 0) {
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

        List<FinancialDocumentItem> draftItems = new ArrayList<>();
        for (PreparedAdjustmentLine preparedLine : preparedLines) {
            draftItems.add(
                    buildAdjustmentItem(adjustmentDocument, invoice, preparedLine)
            );
        }

        if (documentType == FinancialDocumentType.DEBIT_NOTE) {
            applyDebitNoteApplicableOnAdjustments(invoice, draftItems);
            totalAmount = draftItems.stream()
                    .map(FinancialDocumentItem::getNetAmount)
                    .map(this::money)
                    .reduce(ZERO, BigDecimal::add);
            adjustmentDocument.setTotalAmount(totalAmount);
            documentRepo.save(adjustmentDocument);
        }

        List<FinancialDocumentItem> savedItems = new ArrayList<>();
        for (int index = 0; index < preparedLines.size(); index++) {
            PreparedAdjustmentLine preparedLine = preparedLines.get(index);
            FinancialDocumentItem item = draftItems.get(index);
            savedItems.add(itemRepo.save(item));

            if (preparedLine.originalItem() != null && documentType == FinancialDocumentType.CREDIT_NOTE) {
                applyCreditToOriginalItem(
                        preparedLine.originalItem(),
                        preparedLine.amount(),
                        preparedLine.action() == FinancialDocumentItemAdjustmentAction.REMOVE
                );
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
        BigDecimal netAmount = money(original.getNetAmount());
        BigDecimal remaining = money(original.getRemainingAmount());
        BigDecimal paid = money(original.getPaidAmount());

        if (netAmount.signum() <= 0) {
            throw new BadRequestAlertException(
                    "Invoice line has no amount to remove",
                    ENTITY,
                    "adjustment.line.noRemaining"
            );
        }

        if (remaining.signum() <= 0 && paid.signum() <= 0) {
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
                netAmount,
                original.getQuantity(),
                original.getUnitPrice(),
                resolveItemCode(original),
                resolveItemDescription(original),
                money(original.getGrossAmount()),
                money(original.getDiscountAmount()),
                money(original.getTaxAmount())
        );
    }

    /**
     * Full line removal credits the entire net (with tax/discount) but only
     * reduces invoice outstanding by the line's remaining balance.
     */
    private BigDecimal calculateCreditOutstandingReduction(
            List<PreparedAdjustmentLine> preparedLines
    ) {
        return preparedLines.stream()
                .map(line -> {
                    if (line.originalItem() == null) {
                        return line.amount();
                    }

                    if (line.action() == FinancialDocumentItemAdjustmentAction.REMOVE) {
                        return money(line.originalItem().getRemainingAmount());
                    }

                    return line.amount();
                })
                .reduce(ZERO, BigDecimal::add);
    }

    private boolean hasCreditableAdjustmentLines(
            List<PreparedAdjustmentLine> preparedLines
    ) {
        return preparedLines.stream().anyMatch(this::isCreditableAdjustmentLine);
    }

    private boolean isCreditableAdjustmentLine(
            PreparedAdjustmentLine line
    ) {
        if (line.amount() == null || line.amount().signum() <= 0) {
            return false;
        }

        FinancialDocumentItem original = line.originalItem();
        if (original == null) {
            return true;
        }

        BigDecimal remaining = money(original.getRemainingAmount());
        BigDecimal paid = money(original.getPaidAmount());
        BigDecimal net = money(original.getNetAmount());

        return remaining.signum() > 0
                || (net.signum() > 0 && paid.signum() > 0);
    }

    private boolean hasCreditableInvoiceLines(Long invoiceId) {
        return loadCreditableItemsById(invoiceId).values().stream()
                .anyMatch(item ->
                        money(item.getRemainingAmount()).signum() > 0
                                || (money(item.getNetAmount()).signum() > 0
                                        && money(item.getPaidAmount()).signum() > 0)
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
        Currency currency = invoice.getCurrency();
        if (currency == null) {
            throw new BadRequestAlertException(
                    "Currency is required when adding a new service",
                    ENTITY,
                    "adjustment.service.currencyRequired"
            );
        }

        validateNewServiceReference(request);

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

        BigDecimal netAmount = money(quantity.multiply(unitPrice));
        BigDecimal patientShare =
                invoice.getDocumentSubtype() == FinancialDocumentSubtype.PATIENT
                        ? netAmount
                        : ZERO;
        BigDecimal insuranceShare =
                invoice.getDocumentSubtype() == FinancialDocumentSubtype.INSURANCE_CLAIM
                        ? netAmount
                        : ZERO;

        BillingChargeLine chargeLine =
                billingChargeService.createDebitNoteAdjustmentChargeLine(
                        createdService,
                        quantity,
                        unitPrice,
                        patientShare,
                        insuranceShare,
                        "debit-note-add-new-" + createdService.getId()
                );

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

    private void applyDebitNoteApplicableOnAdjustments(
            FinancialDocument invoice,
            List<FinancialDocumentItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Long facilityId =
                patientEncounterRepository
                        .findById(invoice.getEncounterId())
                        .map(PatientEncounter::getFacilityId)
                        .orElse(null);

        if (facilityId == null) {
            LOG.warn(
                    "[DEBIT_NOTE] Skipping invoice tax/discount — encounter {} has no facility",
                    invoice.getEncounterId()
            );
            return;
        }

        LocalDate pricingDate =
                invoice.getCreatedDate() != null
                        ? invoice.getCreatedDate()
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                        : LocalDate.now();

        invoiceItemPricingSnapshotService.captureChargeLineSnapshots(items);
        invoiceApplicableOnAdjustmentService.applyApplicableOnAdjustments(
                items,
                facilityId,
                invoice.getCurrency(),
                pricingDate
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
            BigDecimal creditAmount,
            boolean fullLineRemoval
    ) {
        BigDecimal priorRemaining =
                money(originalItem.getRemainingAmount());
        BigDecimal paid = money(originalItem.getPaidAmount());

        if (fullLineRemoval) {
            BigDecimal paidReduction =
                    creditAmount.subtract(priorRemaining)
                            .max(ZERO)
                            .min(paid);
            originalItem.setPaidAmount(paid.subtract(paidReduction));
            originalItem.setRemainingAmount(ZERO);
            originalItem.setStatus(FinancialDocumentItemStatus.PAID);
        } else {
            BigDecimal remaining = priorRemaining.subtract(creditAmount);
            if (remaining.signum() < 0) {
                remaining = ZERO;
            }

            originalItem.setRemainingAmount(remaining);

            if (remaining.signum() == 0) {
                originalItem.setStatus(FinancialDocumentItemStatus.PAID);
            } else if (paid.signum() > 0) {
                originalItem.setStatus(FinancialDocumentItemStatus.PARTIALLY_PAID);
            } else {
                originalItem.setStatus(FinancialDocumentItemStatus.PENDING);
            }
        }

        itemRepo.save(originalItem);

        syncCreditNoteToChargeLine(originalItem, creditAmount);
    }

    private void syncCreditNoteToChargeLine(
            FinancialDocumentItem creditedItem,
            BigDecimal creditAmount
    ) {
        Long chargeLineId = creditedItem.getBillingChargeLineId();

        if (chargeLineId == null
                && creditedItem.getPatientServiceProductId() != null) {
            chargeLineId =
                    chargeLineRepo
                            .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                                    creditedItem.getPatientServiceProductId(),
                                    EXCLUDED_LINE_STATUSES
                            )
                            .map(BillingChargeLine::getId)
                            .orElse(null);
        }

        if (chargeLineId == null) {
            return;
        }

        billingChargeService.applyCreditNoteChargeLineSync(
                chargeLineId,
                creditAmount,
                money(creditedItem.getNetAmount())
        );
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcileCreditNoteChargeLineSyncForEncounter(Long encounterId) {
        if (encounterId == null) {
            return;
        }

        documentRepo
                .findFirstByEncounterIdAndDocumentTypeOrderByIdDesc(
                        encounterId,
                        FinancialDocumentType.INVOICE
                )
                .ifPresent(invoice ->
                        reconcileCreditNoteChargeLineSync(
                                invoice.getId()
                        )
                );
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void reconcileCreditNoteChargeLineSync(Long invoiceId) {
        FinancialDocument invoice = documentRepo.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return;
        }

        List<FinancialDocument> creditNotes =
                documentRepo.findAllByParentDocumentId(invoiceId).stream()
                        .filter(document ->
                                document.getDocumentType()
                                        == FinancialDocumentType.CREDIT_NOTE
                        )
                        .toList();

        for (FinancialDocument creditNote : creditNotes) {
            itemRepo.findByDocument_Id(creditNote.getId()).forEach(
                    creditItem -> {
                        FinancialDocumentItem sourceItem =
                                resolveCreditedSourceItem(creditItem);
                        if (sourceItem == null) {
                            return;
                        }

                        Long chargeLineId =
                                sourceItem.getBillingChargeLineId();
                        if (chargeLineId == null
                                && sourceItem.getPatientServiceProductId() != null) {
                            chargeLineId =
                                    chargeLineRepo
                                            .findFirstByPatientServiceProduct_IdAndStatusNotInOrderByIdAsc(
                                                    sourceItem.getPatientServiceProductId(),
                                                    EXCLUDED_LINE_STATUSES
                                            )
                                            .map(BillingChargeLine::getId)
                                            .orElse(null);
                        }

                        if (chargeLineId == null) {
                            return;
                        }

                        BillingChargeLine chargeLine =
                                chargeLineRepo.findById(chargeLineId).orElse(null);
                        if (chargeLine == null) {
                            return;
                        }

                        BigDecimal chargeExposure =
                                money(
                                        chargeLine.getPatientResponsibilityAmount()
                                );

                        if (chargeExposure.signum() <= 0) {
                            return;
                        }

                        BigDecimal creditApplied =
                                money(creditItem.getNetAmount());

                        if (creditApplied.signum() <= 0) {
                            return;
                        }

                        billingChargeService.applyCreditNoteChargeLineSync(
                                chargeLineId,
                                creditApplied,
                                money(sourceItem.getNetAmount())
                        );
                    }
            );
        }
    }

    private FinancialDocumentItem resolveCreditedSourceItem(
            FinancialDocumentItem creditItem
    ) {
        if (creditItem.getParentDocumentItemId() != null) {
            return itemRepo
                    .findById(creditItem.getParentDocumentItemId())
                    .orElse(null);
        }

        return null;
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
            Map<Long, BillingChargeLine> chargeLinesByPsp,
            String lineSource
    ) {
        BillingChargeLine chargeLine = chargeLinesByPsp.get(item.getPatientServiceProductId());
        InvoiceItemPricingAdjustmentSnapshot snapshot =
                invoiceItemPricingSnapshotService.readSnapshot(item);

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
                item.getCurrency(),
                snapshot.discounts() == null
                        ? List.of()
                        : snapshot.discounts().stream()
                                .map(entry ->
                                        new InvoiceLineItemResponse.InvoiceLineAppliedDiscount(
                                                entry.source(),
                                                entry.ruleId(),
                                                entry.code(),
                                                entry.name(),
                                                entry.applicableOn() == null
                                                        ? null
                                                        : entry.applicableOn().name(),
                                                entry.discountType() == null
                                                        ? null
                                                        : entry.discountType().name(),
                                                entry.rate(),
                                                entry.fixedAmount(),
                                                entry.appliedAmount()
                                        )
                                )
                                .toList(),
                snapshot.taxes() == null
                        ? List.of()
                        : snapshot.taxes().stream()
                                .map(entry ->
                                        new InvoiceLineItemResponse.InvoiceLineAppliedTax(
                                                entry.source(),
                                                entry.ruleId(),
                                                entry.code(),
                                                entry.name(),
                                                entry.applicableOn() == null
                                                        ? null
                                                        : entry.applicableOn().name(),
                                                entry.taxType() == null
                                                        ? null
                                                        : entry.taxType().name(),
                                                entry.calculationType() == null
                                                        ? null
                                                        : entry.calculationType().name(),
                                                entry.rate(),
                                                entry.fixedAmount(),
                                                entry.appliedAmount()
                                        )
                                )
                                .toList(),
                lineSource
        );
    }

    private Map<Long, FinancialDocumentItem> loadCreditableItemsById(Long invoiceId) {
        List<FinancialDocumentItem> creditable = new ArrayList<>(itemRepo.findByDocument_Id(invoiceId));
        documentRepo.findAllByParentDocumentId(invoiceId).stream()
                .filter(child -> child.getDocumentType() == FinancialDocumentType.DEBIT_NOTE)
                .forEach(debitNote ->
                        creditable.addAll(itemRepo.findByDocument_Id(debitNote.getId()))
                );

        return creditable.stream()
                .collect(Collectors.toMap(FinancialDocumentItem::getId, Function.identity()));
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
        applyCreditNoteFinancialAdjustments(creditNote);
    }

    private void reconcileMissingCreditNoteFinancialAdjustments(Long invoiceId) {
        List<FinancialDocument> pendingCreditNotes =
                documentRepo.findAllByParentDocumentId(invoiceId).stream()
                        .filter(document ->
                                document.getDocumentType()
                                        == FinancialDocumentType.CREDIT_NOTE
                        )
                        .filter(document -> !hasCreditNoteWalletLedger(document))
                        .toList();

        if (pendingCreditNotes.isEmpty()) {
            return;
        }

        pendingCreditNotes.forEach(this::applyCreditNoteFinancialAdjustments);

        LOG.info(
                "[CREDIT_NOTE] Reconciled missing wallet/debit refunds invoiceId={} creditNoteCount={}",
                invoiceId,
                pendingCreditNotes.size()
        );
    }

    private void applyCreditNoteFinancialAdjustments(FinancialDocument creditNote) {
        BigDecimal creditAmount = money(creditNote.getTotalAmount());
        if (creditAmount.signum() <= 0) {
            return;
        }

        BigDecimal remainingAfterWallet =
                applyCreditNoteWalletRefund(creditNote, creditAmount);

        if (remainingAfterWallet.signum() > 0) {
            applyCreditNoteDebitReduction(creditNote, remainingAfterWallet);
        }
    }

    private boolean hasCreditNoteWalletLedger(FinancialDocument creditNote) {
        return billingLedgerRepository
                .findByIdempotencyKey(
                        creditNoteWalletLedgerKey(creditNote.getId())
                )
                .isPresent();
    }

    private String creditNoteWalletLedgerKey(Long creditNoteId) {
        return "CREDIT_NOTE:"
                + creditNoteId
                + ":WALLET_REFUND:LEDGER";
    }

    private BigDecimal applyCreditNoteWalletRefund(
            FinancialDocument creditNote,
            BigDecimal maxAmount
    ) {
        if (hasCreditNoteWalletLedger(creditNote)) {
            return billingLedgerRepository
                    .findByIdempotencyKey(
                            creditNoteWalletLedgerKey(creditNote.getId())
                    )
                    .map(ledger -> maxAmount.subtract(money(ledger.getAmount())).max(ZERO))
                    .orElse(maxAmount);
        }

        BillingWallet wallet =
                billingWalletService.findOptionalByPatientAndCurrency(
                        creditNote.getPatientId(),
                        creditNote.getCurrency()
                );

        if (wallet == null) {
            return maxAmount;
        }

        BigDecimal consumedBefore = money(wallet.getConsumedAmount());
        BigDecimal walletRefund = maxAmount.min(consumedBefore);

        if (walletRefund.signum() <= 0) {
            return maxAmount;
        }

        BigDecimal availableBefore = money(wallet.getAvailableBalance());
        BigDecimal reservedBefore = money(wallet.getReservedBalance());

        BillingWallet updatedWallet =
                billingWalletService.reverseConsumedToAvailable(
                        wallet,
                        walletRefund
                );

        recordCreditNoteWalletLedger(
                creditNote,
                updatedWallet,
                walletRefund,
                availableBefore,
                reservedBefore
        );

        LOG.info(
                "[CREDIT_NOTE] Wallet refunded creditNoteId={} creditNoteNumber={} "
                        + "amount={} available={} reserved={} consumed={}",
                creditNote.getId(),
                creditNote.getDocumentNumber(),
                walletRefund,
                updatedWallet.getAvailableBalance(),
                updatedWallet.getReservedBalance(),
                updatedWallet.getConsumedAmount()
        );

        return maxAmount.subtract(walletRefund);
    }

    private void applyCreditNoteDebitReduction(
            FinancialDocument creditNote,
            BigDecimal maxAmount
    ) {
        BigDecimal remaining = maxAmount;
        if (remaining.signum() <= 0) {
            return;
        }

        BillingCharge charge =
                billingChargeRepository
                        .findFirstByEncounter_IdAndStatusNotInOrderByIdDesc(
                                creditNote.getEncounterId(),
                                EXCLUDED_CHARGE_STATUSES
                        )
                        .orElse(null);

        if (charge == null) {
            return;
        }

        List<BillingAllocation> allocations =
                billingAllocationRepository
                        .findAllByEncounter_IdAndCharge_IdAndStatusInOrderByAllocationDateAscIdAsc(
                                creditNote.getEncounterId(),
                                charge.getId(),
                                ACTIVE_ALLOCATION_STATUSES
                        );

        String reversedBy =
                SecurityUtils.getCurrentUserLogin()
                        .orElse("system");

        String reason =
                StringUtils.hasText(creditNote.getAdjustmentReason())
                        ? creditNote.getAdjustmentReason()
                        : "Credit note "
                                + creditNote.getDocumentNumber();

        for (BillingAllocation allocation : allocations) {
            if (remaining.signum() <= 0) {
                break;
            }

            if (allocation.getAllocationSourceType()
                    != AllocationSourceType.DEBIT) {
                continue;
            }

            if (allocation.getDebitTransactionId() == null) {
                continue;
            }

            BigDecimal allocationRemaining =
                    money(
                            allocation.getRemainingAllocatedAmount()
                    );

            if (allocationRemaining.signum() <= 0) {
                continue;
            }

            BigDecimal reversalAmount =
                    remaining.min(allocationRemaining);

            String requestId =
                    "CREDIT_NOTE:"
                            + creditNote.getId()
                            + ":DEBIT:"
                            + allocation.getId();

            billingDebitService.reverseDebit(
                    allocation.getDebitTransactionId(),
                    reversalAmount,
                    reason,
                    reversedBy,
                    requestId,
                    BillingLedgerSourceChannel.SYSTEM
            );

            remaining = remaining.subtract(reversalAmount);

            LOG.info(
                    "[CREDIT_NOTE] Debit reduced creditNoteId={} creditNoteNumber={} "
                            + "allocationId={} amount={} remaining={}",
                    creditNote.getId(),
                    creditNote.getDocumentNumber(),
                    allocation.getId(),
                    reversalAmount,
                    remaining
            );
        }
    }

    private void recordCreditNoteWalletLedger(
            FinancialDocument creditNote,
            BillingWallet wallet,
            BigDecimal amount,
            BigDecimal walletAvailableBefore,
            BigDecimal walletReservedBefore
    ) {
        PatientEncounter encounter =
                patientEncounterRepository.findById(creditNote.getEncounterId())
                        .orElse(null);

        Patient patient =
                encounter == null
                        ? null
                        : encounter.getPatient();

        UUID transactionGroupId = UUID.randomUUID();
        String idempotencyKey =
                "CREDIT_NOTE:"
                        + creditNote.getId()
                        + ":WALLET_REFUND";

        billingLedgerService.record(
                new BillingLedgerEntryRequest(
                        transactionGroupId,
                        idempotencyKey,
                        creditNoteWalletLedgerKey(creditNote.getId()),

                        patient,
                        encounter,

                        wallet,
                        null,
                        null,

                        null,
                        null,
                        null,

                        null,
                        null,

                        null,
                        null,
                        null,

                        BillingLedgerTransactionType.ADJUSTMENT,

                        BillingLedgerScope.WALLET,

                        amount,
                        creditNote.getCurrency(),

                        amount,
                        ZERO,
                        amount.negate(),
                        ZERO,

                        ZERO,
                        ZERO,
                        ZERO,

                        walletAvailableBefore,
                        money(wallet.getAvailableBalance()),

                        walletReservedBefore,
                        money(wallet.getReservedBalance()),

                        null,
                        null,

                        null,
                        null,

                        BillingLedgerEntryDirection.CREDIT,
                        BillingLedgerEntryCategory.ADJUSTMENT,

                        null,

                        "FINANCIAL_DOCUMENT",
                        creditNote.getId(),
                        creditNote.getDocumentNumber(),

                        "Credit note returned consumed wallet balance to available balance.",

                        creditNote.getAdjustmentReason(),

                        BillingLedgerSourceChannel.SYSTEM
                )
        );
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
                                item.getGrossAmount(),
                                item.getDiscountAmount(),
                                item.getTaxAmount(),
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
