package com.dazzle.asklepios.service;

import com.dazzle.asklepios.service.dto.billing.AppliedDefaultServiceDiscount;
import com.dazzle.asklepios.service.dto.billing.DefaultServiceDiscountLine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carries the authorized default-service discount into {@code BillingPricingService}
 * for the request that is currently pricing those lines, and keeps the amount
 * taken off each saved line so it can be written to the usage log.
 */
public final class DefaultServiceDiscountContext {

    private static final ThreadLocal<AppliedDefaultServiceDiscount> CURRENT = new ThreadLocal<>();

    private static final ThreadLocal<Map<Long, DefaultServiceDiscountLine>> LINES =
            ThreadLocal.withInitial(HashMap::new);

    private DefaultServiceDiscountContext() {
    }

    public static void set(AppliedDefaultServiceDiscount discount) {
        LINES.get().clear();
        if (discount == null) {
            CURRENT.remove();
            return;
        }
        CURRENT.set(discount);
    }

    public static AppliedDefaultServiceDiscount current() {
        return CURRENT.get();
    }

    public static void recordLine(DefaultServiceDiscountLine line) {
        if (line == null || line.patientServiceProductId() == null || current() == null) {
            return;
        }
        LINES.get().put(line.patientServiceProductId(), line);
    }

    public static List<DefaultServiceDiscountLine> lines() {
        return List.copyOf(LINES.get().values());
    }

    public static void clear() {
        CURRENT.remove();
        LINES.remove();
    }
}
