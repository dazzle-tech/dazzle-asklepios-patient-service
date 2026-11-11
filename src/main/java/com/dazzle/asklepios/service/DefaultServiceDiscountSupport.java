package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.DiscountCodeClient;
import com.dazzle.asklepios.client.setup.dto.DiscountCodeSetupDTO;
import com.dazzle.asklepios.client.setup.dto.DiscountCodeValidateRequest;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.DiscountType;
import com.dazzle.asklepios.service.dto.billing.AppliedDefaultServiceDiscount;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Re-checks a discount code with setup-service, then exposes it to pricing
 * for the rest of the current preview or prepare call.
 */
@Service
public class DefaultServiceDiscountSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultServiceDiscountSupport.class);

    private static final String ENTITY_NAME = "defaultServiceBilling";

    private final DiscountCodeClient discountCodeClient;

    public DefaultServiceDiscountSupport(DiscountCodeClient discountCodeClient) {
        this.discountCodeClient = discountCodeClient;
    }

    public void bind(
            Long facilityId,
            Currency currency,
            String discountCode,
            Long authorizedUserId,
            String authorizationToken
    ) {
        DefaultServiceDiscountContext.clear();
        if (!hasAny(discountCode, authorizedUserId, authorizationToken)) {
            return;
        }
        if (isBlank(discountCode) || authorizedUserId == null || isBlank(authorizationToken)) {
            throw new BadRequestAlertException(
                    "Discount code, authorized user, and authorization are all required.",
                    ENTITY_NAME,
                    "discountcode"
            );
        }
        if (currency == null) {
            throw new BadRequestAlertException(
                    "Facility currency is required before a discount code can be applied.",
                    ENTITY_NAME,
                    "discountcurrency"
            );
        }

        DiscountCodeSetupDTO discount = load(facilityId, discountCode.trim(), authorizedUserId, authorizationToken);
        if (discount.discountType() == null) {
            throw new BadRequestAlertException(
                    "Discount code has no discount type.",
                    ENTITY_NAME,
                    "discounttype"
            );
        }
        if (discount.discountType() == DiscountType.FIXED_AMOUNT
                && discount.currency() != null
                && discount.currency() != currency) {
            throw new BadRequestAlertException(
                    "Discount code currency " + discount.currency()
                            + " does not match the facility currency " + currency + ".",
                    ENTITY_NAME,
                    "discountcurrency"
            );
        }

        DefaultServiceDiscountContext.set(new AppliedDefaultServiceDiscount(
                discount.id(),
                discount.code(),
                discount.name(),
                discount.discountType(),
                discount.percentage(),
                discount.fixedAmount(),
                discount.currency()
        ));
        LOG.info(
                "[DEFAULT_SERVICE_DISCOUNT] facilityId={} code={} userId={} type={}",
                facilityId,
                discount.code(),
                authorizedUserId,
                discount.discountType()
        );
    }

    public void clear() {
        DefaultServiceDiscountContext.clear();
    }

    private DiscountCodeSetupDTO load(
            Long facilityId,
            String code,
            Long userId,
            String authorizationToken
    ) {
        try {
            return discountCodeClient.validate(
                    new DiscountCodeValidateRequest(facilityId, code, userId, authorizationToken)
            );
        } catch (FeignException.BadRequest | FeignException.NotFound ex) {
            throw new BadRequestAlertException(
                    problemTitle(ex, "Discount code could not be applied."),
                    ENTITY_NAME,
                    "discountcode"
            );
        } catch (FeignException ex) {
            LOG.warn(
                    "[DEFAULT_SERVICE_DISCOUNT] Setup validation failed status={} body={}",
                    ex.status(),
                    ex.contentUTF8()
            );
            throw new BadRequestAlertException(
                    "Discount code could not be verified. Try again.",
                    ENTITY_NAME,
                    "discountcode"
            );
        }
    }

    private String problemTitle(FeignException exception, String fallback) {
        String body = exception.contentUTF8();
        if (body == null || body.isBlank()) {
            return fallback;
        }
        int titleKey = body.indexOf("\"title\"");
        if (titleKey < 0) {
            return fallback;
        }
        int colon = body.indexOf(':', titleKey);
        int firstQuote = body.indexOf('"', colon + 1);
        int secondQuote = firstQuote < 0 ? -1 : body.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || secondQuote < 0) {
            return fallback;
        }
        String title = body.substring(firstQuote + 1, secondQuote).trim();
        return title.isBlank() ? fallback : title;
    }

    private boolean hasAny(String discountCode, Long authorizedUserId, String authorizationToken) {
        return !isBlank(discountCode) || authorizedUserId != null || !isBlank(authorizationToken);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
