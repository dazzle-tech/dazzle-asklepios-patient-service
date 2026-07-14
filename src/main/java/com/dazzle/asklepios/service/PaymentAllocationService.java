package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.FinancialDocumentItem;
import com.dazzle.asklepios.domain.PatientPaymentAllocation;
import com.dazzle.asklepios.repository.FinancialDocumentItemRepository;
import com.dazzle.asklepios.repository.PatientPaymentAllocationRepository;
import com.dazzle.asklepios.service.dto.patientPayments.PaymentAllocationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAllocationService {

    private final FinancialDocumentItemRepository itemRepo;
    private final PatientPaymentAllocationRepository allocationRepo;

    public void allocateManually(
            Long paymentId,
            List<PaymentAllocationDTO> allocations
    ) {

        for (PaymentAllocationDTO dto : allocations) {

            FinancialDocumentItem item =
                    itemRepo.findById(dto.documentItemId())
                            .orElseThrow(() -> new IllegalStateException("Item not found"));

            BigDecimal allocatedAmount = safe(dto.amount());

            // ✅ compute already allocated
            BigDecimal alreadyPaid =
                    allocationRepo.sumPaidForItem(dto.documentItemId());

            BigDecimal remaining =
                    item.getNetAmount().subtract(safe(alreadyPaid));

            // ❗ validation
            if (allocatedAmount.compareTo(remaining) > 0) {
                throw new IllegalStateException(
                        "Allocation exceeds remaining amount for item " + item.getId()
                );
            }

            // ✅ save allocation
            allocationRepo.save(
                    PatientPaymentAllocation.builder()
                            .paymentId(paymentId)
                            .documentItemId(item.getId())
                            .paidFromAmount(allocatedAmount)
                            .paidFromBalance(BigDecimal.ZERO)
                            .lastModifiedDate(Instant.now())
                            .build()
            );
        }
    }

    private BigDecimal safe(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
