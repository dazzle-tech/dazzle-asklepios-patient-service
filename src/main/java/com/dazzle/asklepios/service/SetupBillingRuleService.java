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
     * Resolve the billing rule for a catalog item.
     *
     * Uses the catalog-specific billingRuleId when present; otherwise falls
     * back to the default rule for the billing item type.
     */
    public BillingRuleResolveResponse resolveForCatalogItem(
            BillingItemTypes billingItemType,
            Long catalogBillingRuleId
    ) {
        if (billingItemType == null) {
            throw new BadRequestAlertException(
                    "Billing item type is required.",
                    ENTITY_NAME,
                    "billingItemType.required"
            );
        }

        BillingRuleResponse response =
                catalogBillingRuleId != null
                        ? loadRuleById(
                                catalogBillingRuleId,
                                billingItemType
                        )
                        : loadDefaultRule(
                                billingItemType
                        );

        validateBasicResponse(response);

        BillingItemTypes resolvedType =
                parseBillingItemType(
                        response.billingItemType()
                );

        if (resolvedType != billingItemType) {
            throw new BadRequestAlertException(
                    "Resolved billing rule item type "
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

    /**
     * Resolve the billing rule for a persisted patient service/product.
     */
    public BillingRuleResolveResponse resolve(
            PatientServiceAndProduct item,
            Long catalogBillingRuleId
    ) {
        validateItem(item);

        return resolveForCatalogItem(
                item.getBillingItemType(),
                catalogBillingRuleId
        );
    }

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

        return resolveForCatalogItem(
                item.getBillingItemType(),
                null
        );
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

        validateDefaultRuleResponse(response);

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

    private BillingRuleResponse loadRuleById(
            Long billingRuleId,
            BillingItemTypes expectedItemType
    ) {
        try {
            BillingRuleResponse response =
                    billingRuleClient.getById(
                            billingRuleId
                    );

            if (response == null) {
                throw new NotFoundAlertException(
                        "Billing rule was not found with id "
                                + billingRuleId,
                        ENTITY_NAME,
                        "billingRule.notfound"
                );
            }

            return response;

        } catch (FeignException.NotFound exception) {
            throw new NotFoundAlertException(
                    "Billing rule was not found with id "
                            + billingRuleId,
                    ENTITY_NAME,
                    "billingRule.notfound"
            );

        } catch (FeignException exception) {
            LOG.error(
                    "[LOAD_BY_ID] Setup Service call failed "
                            + "ruleId={} itemType={} status={}",
                    billingRuleId,
                    expectedItemType,
                    exception.status(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to retrieve billing rule "
                            + billingRuleId
                            + " from Setup Service.",
                    ENTITY_NAME,
                    "setupService.billingRule.failed"
            );
        }
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

            validateDefaultRuleResponse(response);

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

    private void validateDefaultRuleResponse(
            BillingRuleResponse response
    ) {
        validateBasicResponse(response);

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