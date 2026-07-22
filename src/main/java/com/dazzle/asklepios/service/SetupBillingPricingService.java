package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.SetupBillingPricingClient;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SetupBillingPricingService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    SetupBillingPricingService.class
            );

    private static final String ENTITY_NAME =
            "setupBillingPricing";

    private final SetupBillingPricingClient
            setupBillingPricingClient;

    public BillingPricingResolveResponse resolve(
            BillingPricingResolveRequest request
    ) {
        validateRequest(request);

        LOG.debug(
                "[RESOLVE] Calling Setup Service pricing facilityId={} patientId={} encounterId={} itemType={} sourceId={}",
                request.facilityId(),
                request.patientId(),
                request.encounterId(),
                request.billingItemType(),
                request.sourceId()
        );

        try {
            BillingPricingResolveResponse response =
                    setupBillingPricingClient.resolve(
                            request
                    );

            validateResponse(response);

            LOG.info(
                    "[RESOLVE] Setup pricing resolved priceListId={} priceItemId={} unitPrice={} taxId={} discountId={}",
                    response.priceListId(),
                    response.priceListItemId(),
                    response.unitPrice(),
                    response.taxId(),
                    response.discountId()
            );

            return response;

        } catch (FeignException exception) {
            LOG.error(
                    "[RESOLVE] Setup pricing call failed status={} response={}",
                    exception.status(),
                    exception.contentUTF8(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to resolve billing pricing from Setup Service.",
                    ENTITY_NAME,
                    "setup.call.failed"
            );
        }
    }

    private void validateRequest(
            BillingPricingResolveRequest request
    ) {
        if (request == null) {
            throw new BadRequestAlertException(
                    "Pricing request is required.",
                    ENTITY_NAME,
                    "request.required"
            );
        }

        if (request.facilityId() == null) {
            throw new BadRequestAlertException(
                    "Facility ID is required.",
                    ENTITY_NAME,
                    "facility.required"
            );
        }

        if (request.patientId() == null) {
            throw new BadRequestAlertException(
                    "Patient ID is required.",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        if (request.encounterId() == null) {
            throw new BadRequestAlertException(
                    "Encounter ID is required.",
                    ENTITY_NAME,
                    "encounter.required"
            );
        }

        if (request.billingItemType() == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        if (request.sourceId() == null) {
            throw new BadRequestAlertException(
                    "Source ID is required.",
                    ENTITY_NAME,
                    "sourceId.required"
            );
        }

        if (request.currency() == null) {
            throw new BadRequestAlertException(
                    "Currency is required.",
                    ENTITY_NAME,
                    "currency.required"
            );
        }
    }

    private void validateResponse(
            BillingPricingResolveResponse response
    ) {
        if (response == null) {
            throw new BadRequestAlertException(
                    "Setup Service returned no pricing data.",
                    ENTITY_NAME,
                    "response.empty"
            );
        }

        if (response.priceListId() == null) {
            throw new BadRequestAlertException(
                    "Resolved price-list ID is missing.",
                    ENTITY_NAME,
                    "priceListId.missing"
            );
        }

        if (response.priceListItemId() == null) {
            throw new BadRequestAlertException(
                    "Resolved price-list item ID is missing.",
                    ENTITY_NAME,
                    "priceListItemId.missing"
            );
        }

        if (response.unitPrice() == null
                || response.unitPrice().signum() < 0) {
            throw new BadRequestAlertException(
                    "Resolved unit price is invalid.",
                    ENTITY_NAME,
                    "unitPrice.invalid"
            );
        }

        if (response.currency() == null) {
            throw new BadRequestAlertException(
                    "Resolved currency is missing.",
                    ENTITY_NAME,
                    "currency.missing"
            );
        }
    }
}