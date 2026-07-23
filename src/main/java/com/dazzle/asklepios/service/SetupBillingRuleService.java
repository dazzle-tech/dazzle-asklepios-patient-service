package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.BillingRuleClient;
import com.dazzle.asklepios.client.setup.dto.BillingRuleResponse;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.service.dto.billing.BillingRuleResolveResponse;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.dazzle.asklepios.web.rest.errors.NotFoundAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SetupBillingRuleService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    SetupBillingRuleService.class
            );

    private static final String ENTITY_NAME =
            "setupBillingRule";

    private final BillingRuleClient billingRuleClient;

    /**
     * Temporary resolution strategy:
     *
     * Always resolve the default billing rule
     * using the PatientServiceAndProduct billing item type.
     */
    public BillingRuleResolveResponse resolve(
            PatientServiceAndProduct item
    ) {
        validateItem(item);

        BillingRuleResponse response =
                loadDefaultRule(
                        item.getBillingItemType()
                );

        validateResponse(
                item,
                response
        );

        BillingRuleResolveResponse result =
                mapToResolveResponse(response);

        LOG.info(
                "[RESOLVE_DEFAULT] Billing rule resolved "
                        + "pspId={} ruleId={} ruleName={} "
                        + "itemType={} trigger={}",
                item.getId(),
                result.billingRuleId(),
                result.billingRuleName(),
                result.billingItemType(),
                result.billingTrigger()
        );

        return result;
    }

    /**
     * Optional helper for resolving the default rule directly
     * by billing item type.
     */
    public BillingRuleResolveResponse resolveDefault(
            BillingItemTypes billingItemType
    ) {
        if (billingItemType == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        BillingRuleResponse response =
                loadDefaultRule(
                        billingItemType
                );

        validateBasicResponse(response);

        BillingItemTypes resolvedType =
                parseBillingItemType(
                        response.billingItemType()
                );

        if (resolvedType != billingItemType) {
            throw new BadRequestAlertException(
                    "Resolved default billing rule item type "
                            + resolvedType
                            + " does not match requested item type "
                            + billingItemType
                            + ".",
                    ENTITY_NAME,
                    "billingRule.itemType.mismatch"
            );
        }

        return mapToResolveResponse(response);
    }

    private BillingRuleResponse loadDefaultRule(
            BillingItemTypes billingItemType
    ) {
        try {
            BillingRuleResponse response =
                    billingRuleClient.getDefault(
                            billingItemType.name()
                    );

            if (response == null) {
                throw new NotFoundAlertException(
                        "Setup Service returned no default billing rule "
                                + "for item type "
                                + billingItemType,
                        ENTITY_NAME,
                        "defaultBillingRule.notfound"
                );
            }

            return response;

        } catch (FeignException.NotFound exception) {
            throw new NotFoundAlertException(
                    "Default billing rule was not found for item type "
                            + billingItemType,
                    ENTITY_NAME,
                    "defaultBillingRule.notfound"
            );

        } catch (FeignException exception) {
            LOG.error(
                    "[LOAD_DEFAULT] Setup Service call failed "
                            + "itemType={} status={}",
                    billingItemType,
                    exception.status(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to retrieve the default billing rule "
                            + "from Setup Service.",
                    ENTITY_NAME,
                    "setupService.defaultBillingRule.failed"
            );
        }
    }

    private BillingRuleResolveResponse mapToResolveResponse(
            BillingRuleResponse response
    ) {
        return new BillingRuleResolveResponse(
                response.id(),
                response.name(),
                parseBillingItemType(
                        response.billingItemType()
                ),
                response.billingTrigger()
        );
    }

    private void validateItem(
            PatientServiceAndProduct item
    ) {
        if (item == null) {
            throw new BadRequestAlertException(
                    "Patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.required"
            );
        }

        if (item.getId() == null) {
            throw new BadRequestAlertException(
                    "Persisted patient service/product is required.",
                    ENTITY_NAME,
                    "patientServiceProduct.notPersisted"
            );
        }

        if (item.getBillingItemType() == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }
    }

    private void validateResponse(
            PatientServiceAndProduct item,
            BillingRuleResponse response
    ) {
        validateBasicResponse(response);

        BillingItemTypes resolvedType =
                parseBillingItemType(
                        response.billingItemType()
                );

        if (resolvedType
                != item.getBillingItemType()) {

            throw new BadRequestAlertException(
                    "Resolved billing rule item type "
                            + resolvedType
                            + " does not match patient item type "
                            + item.getBillingItemType()
                            + ".",
                    ENTITY_NAME,
                    "billingRule.itemType.mismatch"
            );
        }
    }

    private void validateBasicResponse(
            BillingRuleResponse response
    ) {
        if (response == null) {
            throw new NotFoundAlertException(
                    "Billing rule response is empty.",
                    ENTITY_NAME,
                    "billingRule.notfound"
            );
        }

        if (response.id() == null) {
            throw new BadRequestAlertException(
                    "Resolved billing rule ID is missing.",
                    ENTITY_NAME,
                    "billingRuleId.missing"
            );
        }

        if (response.name() == null
                || response.name().isBlank()) {
            throw new BadRequestAlertException(
                    "Resolved billing rule name is missing.",
                    ENTITY_NAME,
                    "billingRuleName.missing"
            );
        }

        if (response.billingItemType() == null
                || response.billingItemType().isBlank()) {
            throw new BadRequestAlertException(
                    "Resolved billing item type is missing.",
                    ENTITY_NAME,
                    "billingItemType.missing"
            );
        }

        if (response.billingTrigger() == null) {
            throw new BadRequestAlertException(
                    "Resolved billing trigger is missing.",
                    ENTITY_NAME,
                    "billingTrigger.missing"
            );
        }

        if (!Boolean.TRUE.equals(
                response.isDefault()
        )) {
            throw new BadRequestAlertException(
                    "Setup Service returned a billing rule "
                            + "that is not marked as default.",
                    ENTITY_NAME,
                    "billingRule.notDefault"
            );
        }
    }

    private BillingItemTypes parseBillingItemType(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            throw new BadRequestAlertException(
                    "Billing item type value is missing.",
                    ENTITY_NAME,
                    "billingItemType.missing"
            );
        }

        try {
            return BillingItemTypes.valueOf(
                    value.trim().toUpperCase()
            );

        } catch (IllegalArgumentException exception) {
            throw new BadRequestAlertException(
                    "Unsupported billing item type returned "
                            + "by Setup Service: "
                            + value,
                    ENTITY_NAME,
                    "billingItemType.unsupported"
            );
        }
    }
}