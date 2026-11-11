package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.DiscountCodeUsage;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.DiscountCodeUsageRepository;
import com.dazzle.asklepios.service.dto.billing.AppliedDefaultServiceDiscount;
import com.dazzle.asklepios.service.dto.billing.BillingOperationResult;
import com.dazzle.asklepios.service.dto.billing.DefaultServiceDiscountLine;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesRequest;
import com.dazzle.asklepios.service.dto.billing.PrepareDefaultServicesResult;
import com.dazzle.asklepios.web.rest.vm.billing.DiscountCodeUsageVM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes one row per default service or procedure that a discount code reduced
 * when the visit is prepared.
 */
@Service
public class DiscountCodeUsageService {

    private static final Logger LOG = LoggerFactory.getLogger(DiscountCodeUsageService.class);

    private final DiscountCodeUsageRepository discountCodeUsageRepository;
    private final BillingChargeLineRepository billingChargeLineRepository;

    public DiscountCodeUsageService(
            DiscountCodeUsageRepository discountCodeUsageRepository,
            BillingChargeLineRepository billingChargeLineRepository
    ) {
        this.discountCodeUsageRepository = discountCodeUsageRepository;
        this.billingChargeLineRepository = billingChargeLineRepository;
    }

    @Transactional(readOnly = true)
    public List<DiscountCodeUsageVM> findByEncounterId(Long encounterId) {
        List<DiscountCodeUsage> usages =
                discountCodeUsageRepository.findByEncounterIdOrderByCreatedDateDescIdDesc(encounterId);
        List<Long> chargeLineIds = usages.stream()
                .map(DiscountCodeUsage::getChargeLineId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> itemDescriptions = chargeLineIds.isEmpty()
                ? Map.of()
                : billingChargeLineRepository.findReadableByIdIn(chargeLineIds).stream()
                        .collect(Collectors.toMap(
                                BillingChargeLine::getId,
                                BillingChargeLine::getItemDescription,
                                (left, right) -> left
                        ));

        return usages.stream()
                .map(usage -> DiscountCodeUsageVM.of(usage, itemDescriptions.get(usage.getChargeLineId())))
                .toList();
    }

    public void log(
            Long encounterId,
            PrepareDefaultServicesRequest request,
            PrepareDefaultServicesResult result
    ) {
        AppliedDefaultServiceDiscount discount = DefaultServiceDiscountContext.current();
        List<DefaultServiceDiscountLine> lines = DefaultServiceDiscountContext.lines();
        if (discount == null
                || discount.discountCodeId() == null
                || request == null
                || request.authorizedUserId() == null
                || lines.isEmpty()) {
            return;
        }

        int saved = 0;
        for (DefaultServiceDiscountLine line : lines) {
            BigDecimal discountAmount = defaultZero(line.discountAmount());
            var existing = discountCodeUsageRepository
                    .findByPatientServiceProductIdAndDiscountCodeId(
                            line.patientServiceProductId(),
                            discount.discountCodeId()
                    );
            if (discountAmount.signum() <= 0 && existing.isEmpty()) {
                continue;
            }

            DiscountCodeUsage usage = existing.orElseGet(DiscountCodeUsage::new);

            usage.setDiscountCodeId(discount.discountCodeId());
            usage.setFacilityId(request.facilityId());
            usage.setPatientId(request.patientId());
            usage.setEncounterId(encounterId);
            usage.setPatientServiceProductId(line.patientServiceProductId());
            usage.setChargeLineId(chargeLineId(result, line.patientServiceProductId()));
            usage.setServiceId(line.serviceId());
            usage.setProcedureId(line.procedureId());
            usage.setAuthorizedUserId(request.authorizedUserId());
            usage.setCode(discount.code());
            usage.setDiscountName(discount.name());
            usage.setDiscountType(discount.discountType());
            usage.setPercentage(discount.percentage());
            usage.setFixedAmount(discount.fixedAmount());
            usage.setCurrency(request.currency());
            usage.setGrossAmount(defaultZero(line.grossAmount()));
            usage.setDiscountAmount(discountAmount);
            discountCodeUsageRepository.save(usage);
            saved++;
        }

        if (saved == 0) {
            return;
        }

        LOG.info(
                "[DISCOUNT_CODE_USAGE] encounterId={} code={} lines={}",
                encounterId,
                discount.code(),
                saved
        );
    }

    private Long chargeLineId(PrepareDefaultServicesResult result, Long patientServiceProductId) {
        if (result == null || patientServiceProductId == null) {
            return null;
        }
        if (result.items() != null) {
            for (PrepareDefaultServicesResult.PreparedDefaultServiceResult item : result.items()) {
                Long chargeLineId = chargeLineId(item.patientServiceProductId(), item.billingResult(), patientServiceProductId);
                if (chargeLineId != null) {
                    return chargeLineId;
                }
            }
        }
        if (result.procedures() != null) {
            for (PrepareDefaultServicesResult.PreparedDefaultProcedureResult item : result.procedures()) {
                Long chargeLineId = chargeLineId(item.patientServiceProductId(), item.billingResult(), patientServiceProductId);
                if (chargeLineId != null) {
                    return chargeLineId;
                }
            }
        }
        return null;
    }

    private Long chargeLineId(
            Long patientServiceProductId,
            BillingOperationResult billingResult,
            Long expectedPatientServiceProductId
    ) {
        if (billingResult == null || !expectedPatientServiceProductId.equals(patientServiceProductId)) {
            return null;
        }
        return billingResult.chargeLineId();
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
