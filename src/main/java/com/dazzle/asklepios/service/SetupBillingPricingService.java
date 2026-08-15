package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.SetupBillingPricingClient;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveRequest;
import com.dazzle.asklepios.client.setup.dto.BillingPricingResolveResponse;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.Currency;
import com.dazzle.asklepios.domain.enumeration.billing.BillingPriceSource;
import com.dazzle.asklepios.domain.enumeration.billing.PricingSource;
import com.dazzle.asklepios.service.dto.billing.ResolvedBillingPrice;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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

    private final ServiceClient
            serviceClient;

    private final ProcedureClient
            procedureClient;

    private final DiagnosticTestClient
            diagnosticTestClient;

    private final BrandMedicationClient
            brandMedicationClient;

    /*
     * ============================================================
     * RESOLVE WITH FALLBACK
     * ============================================================
     */

    /**
     * Resolves the final billing price using:
     *
     * 1. Applicable Price List item.
     * 2. Setup item base-price fallback.
     * 3. Error if neither source contains a valid price.
     */
    public ResolvedBillingPrice resolveOrFallback(
            BillingPricingResolveRequest request
    ) {
        validateRequest(request);

        BigDecimal setupUnitPrice =
                readSetupUnitPrice(
                        request
                );

        BillingPricingResolveResponse priceListResponse =
                tryResolveFromPriceList(
                        request
                );

        if (hasValidPriceListResult(
                priceListResponse
        )) {
            LOG.info(
                    "[RESOLVE] Price resolved from Price List "
                            + "itemType={} sourceId={} coverageType={} "
                            + "patientInsuranceId={} payerId={} "
                            + "priceListId={} priceListItemId={} "
                            + "unitPrice={} setupUnitPrice={} currency={}",
                    request.billingItemType(),
                    request.sourceId(),
                    request.coverageType(),
                    request.patientInsuranceId(),
                    request.payerId(),
                    priceListResponse.priceListId(),
                    priceListResponse.priceListItemId(),
                    priceListResponse.unitPrice(),
                    setupUnitPrice,
                    priceListResponse.currency()
            );

            return new ResolvedBillingPrice(
                    priceListResponse.unitPrice(),

                    priceListResponse.currency(),

                    BillingPriceSource.PRICE_LIST,

                    request.sourceId(),

                    priceListResponse.priceListId(),

                    priceListResponse.priceListItemId(),

                    priceListResponse,

                    setupUnitPrice,

                    priceListResponse.itemCode(),

                    priceListResponse.itemName()
            );
        }

        LOG.info(
                "[RESOLVE] No applicable Price List item found. "
                        + "Using Setup fallback facilityId={} encounterId={} "
                        + "coverageType={} itemType={} sourceId={} "
                        + "patientInsuranceId={} payerId={} currency={} "
                        + "setupUnitPrice={}",
                request.facilityId(),
                request.encounterId(),
                request.coverageType(),
                request.billingItemType(),
                request.sourceId(),
                request.patientInsuranceId(),
                request.payerId(),
                request.currency(),
                setupUnitPrice
        );

        return resolveFromSetupFallback(
                request
        );
    }

    /*
     * ============================================================
     * STRICT PRICE-LIST RESOLUTION
     * ============================================================
     */

    /**
     * Strict resolution retained for workflows that explicitly
     * require a Price List item and do not permit fallback.
     */
    public BillingPricingResolveResponse resolve(
            BillingPricingResolveRequest request
    ) {
        validateRequest(request);

        BillingPricingResolveResponse response =
                callPricingResolver(
                        request
                );

        validateStrictPriceListResponse(
                response
        );

        return response;
    }

    /*
     * ============================================================
     * PRICE-LIST RESOLUTION
     * ============================================================
     */

    private BillingPricingResolveResponse
    tryResolveFromPriceList(
            BillingPricingResolveRequest request
    ) {
        try {
            BillingPricingResolveResponse response =
                    callPricingResolver(
                            request
                    );

            if (!hasValidPriceListResult(
                    response
            )) {
                LOG.info(
                        "[PRICE_LIST] Resolver returned no usable "
                                + "Price List item itemType={} sourceId={}",
                        request.billingItemType(),
                        request.sourceId()
                );

                return null;
            }

            return response;

        } catch (FeignException.NotFound exception) {
            LOG.info(
                    "[PRICE_LIST] Price List item not found "
                            + "itemType={} sourceId={} status={}",
                    request.billingItemType(),
                    request.sourceId(),
                    exception.status()
            );

            return null;

        } catch (FeignException exception) {
            /*
             * Fallback must only happen when pricing is genuinely
             * not found.
             *
             * Do not silently use Setup price for:
             *
             * - timeout
             * - 401 / 403
             * - Setup Service failure
             * - unexpected 5xx errors
             */
            LOG.error(
                    "[PRICE_LIST] Setup pricing call failed "
                            + "status={} response={}",
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

    private BillingPricingResolveResponse callPricingResolver(
            BillingPricingResolveRequest request
    ) {
        LOG.debug(
                "[PRICE_LIST] Calling pricing resolver "
                        + "facilityId={} patientId={} encounterId={} "
                        + "itemType={} sourceId={} patientInsuranceId={} "
                        + "payerId={} currency={}",
                request.facilityId(),
                request.patientId(),
                request.encounterId(),
                request.billingItemType(),
                request.sourceId(),
                request.patientInsuranceId(),
                request.payerId(),
                request.currency()
        );

        return setupBillingPricingClient.resolve(
                request
        );
    }

    /*
     * ============================================================
     * SETUP FALLBACK
     * ============================================================
     */

    private ResolvedBillingPrice resolveFromSetupFallback(
            BillingPricingResolveRequest request
    ) {
        return switch (
                request.billingItemType()
                ) {
            case SERVICE ->
                    resolveServiceFallback(
                            request
                    );

            case PROCEDURE ->
                    resolveProcedureFallback(
                            request
                    );

            case MEDICATION ->
                    resolveBrandMedicationFallback(
                            request
                    );

            case LABORATORY,
                 RADIOLOGY,
                 PATHOLOGY ->
                    resolveDiagnosticTestFallback(
                            request
                    );
        };
    }

    /*
     * ============================================================
     * SERVICE FALLBACK
     * ============================================================
     */

    private ResolvedBillingPrice resolveServiceFallback(
            BillingPricingResolveRequest request
    ) {
        ServiceSetupDTO service =
                callSetupItem(
                        request.billingItemType(),
                        request.sourceId(),
                        () ->
                                serviceClient
                                        .getServiceDetails(
                                                request.sourceId()
                                        )
                );

        validateActive(
                service.isActive(),
                request.billingItemType(),
                request.sourceId()
        );

        BigDecimal price =
                service.price();

        return buildFallbackResult(
                request,
                price,
                service.currency(),
                service.id(),
                service.code(),
                service.name()
        );
    }

    /*
     * ============================================================
     * PROCEDURE FALLBACK
     * ============================================================
     */

    private ResolvedBillingPrice resolveProcedureFallback(
            BillingPricingResolveRequest request
    ) {
        ProcedureSetupDTO procedure =
                callSetupItem(
                        request.billingItemType(),
                        request.sourceId(),
                        () ->
                                procedureClient
                                        .getProcedure(
                                                request.sourceId()
                                        )
                );

        validateActive(
                procedure.isActive(),
                request.billingItemType(),
                request.sourceId()
        );

        BigDecimal price =
                procedure.price();

        return buildFallbackResult(
                request,
                price,
                procedure.currency(),
                procedure.id(),
                procedure.code(),
                procedure.name()
        );
    }

    /*
     * ============================================================
     * DIAGNOSTIC TEST FALLBACK
     * ============================================================
     */

    private ResolvedBillingPrice
    resolveDiagnosticTestFallback(
            BillingPricingResolveRequest request
    ) {
        DiagnosticTestSetupDTO diagnosticTest =
                callSetupItem(
                        request.billingItemType(),
                        request.sourceId(),
                        () ->
                                diagnosticTestClient
                                        .getDiagnosticTest(
                                                request.sourceId()
                                        )
                );

        validateActive(
                diagnosticTest.isActive(),
                request.billingItemType(),
                request.sourceId()
        );

        validateDiagnosticTestType(
                request.billingItemType(),
                diagnosticTest
        );

        return buildFallbackResult(
                request,
                diagnosticTest.price(),
                diagnosticTest.currency(),
                diagnosticTest.id(),
                diagnosticTest.internalCode(),
                diagnosticTest.name()
        );
    }

    /*
     * ============================================================
     * MEDICATION FALLBACK
     * ============================================================
     */

    private ResolvedBillingPrice
    resolveBrandMedicationFallback(
            BillingPricingResolveRequest request
    ) {
        BrandMedicationSetupDTO medication =
                callSetupItem(
                        request.billingItemType(),
                        request.sourceId(),
                        () ->
                                brandMedicationClient
                                        .getBrandMedication(
                                                request.sourceId()
                                        )
                );

        validateActive(
                medication.isActive(),
                request.billingItemType(),
                request.sourceId()
        );

        Currency currency =
                parseCurrency(
                        medication.currency()
                );

        return buildFallbackResult(
                request,
                medication.price(),
                currency,
                medication.id(),
                medication.code(),
                medication.name()
        );
    }

    /*
     * ============================================================
     * BUILD FALLBACK RESULT
     * ============================================================
     */

    private ResolvedBillingPrice buildFallbackResult(
            BillingPricingResolveRequest request,
            BigDecimal setupUnitPrice,
            Currency setupCurrency,
            Long setupSourceId,
            String setupItemCode,
            String setupItemName
    ) {
        if (setupUnitPrice == null
                || setupUnitPrice.signum() < 0) {
            throw new BadRequestAlertException(
                    "No valid price was found in the Price List or Setup item.",
                    ENTITY_NAME,
                    "price.notfound"
            );
        }

        if (setupCurrency == null) {
            throw new BadRequestAlertException(
                    "Setup item currency is missing.",
                    ENTITY_NAME,
                    "fallback.currency.missing"
            );
        }

        if (setupCurrency
                != request.currency()) {
            throw new BadRequestAlertException(
                    "Setup fallback currency "
                            + setupCurrency
                            + " does not match billing currency "
                            + request.currency()
                            + ".",
                    ENTITY_NAME,
                    "fallback.currency.mismatch"
            );
        }

        if (setupSourceId == null) {
            throw new BadRequestAlertException(
                    "Setup source ID is missing.",
                    ENTITY_NAME,
                    "fallback.setupSourceId.missing"
            );
        }

        if (!setupSourceId.equals(
                request.sourceId()
        )) {
            throw new BadRequestAlertException(
                    "Setup response ID does not match requested source ID.",
                    ENTITY_NAME,
                    "fallback.setupSourceId.mismatch"
            );
        }

        LOG.info(
                "[SETUP_FALLBACK] Price resolved from Setup "
                        + "itemType={} sourceId={} "
                        + "unitPrice={} currency={}",
                request.billingItemType(),
                setupSourceId,
                setupUnitPrice,
                setupCurrency
        );

        return new ResolvedBillingPrice(
                setupUnitPrice,

                setupCurrency,

                BillingPriceSource.SETUP_FALLBACK,

                setupSourceId,

                null,

                null,

                null,

                setupUnitPrice,

                blankToNull(setupItemCode),

                blankToNull(setupItemName)
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private BigDecimal readSetupUnitPrice(
            BillingPricingResolveRequest request
    ) {
        return switch (
                request.billingItemType()
                ) {
            case SERVICE -> {
                ServiceSetupDTO service =
                        callSetupItem(
                                request.billingItemType(),
                                request.sourceId(),
                                () ->
                                        serviceClient
                                                .getServiceDetails(
                                                        request.sourceId()
                                                )
                        );
                yield service.price();
            }
            case PROCEDURE -> {
                ProcedureSetupDTO procedure =
                        callSetupItem(
                                request.billingItemType(),
                                request.sourceId(),
                                () ->
                                        procedureClient
                                                .getProcedure(
                                                        request.sourceId()
                                                )
                        );
                yield procedure.price();
            }
            case MEDICATION -> {
                BrandMedicationSetupDTO medication =
                        callSetupItem(
                                request.billingItemType(),
                                request.sourceId(),
                                () ->
                                        brandMedicationClient
                                                .getBrandMedication(
                                                        request.sourceId()
                                                )
                        );
                yield medication.price();
            }
            case LABORATORY,
                 RADIOLOGY,
                 PATHOLOGY -> {
                DiagnosticTestSetupDTO diagnosticTest =
                        callSetupItem(
                                request.billingItemType(),
                                request.sourceId(),
                                () ->
                                        diagnosticTestClient
                                                .getDiagnosticTest(
                                                        request.sourceId()
                                                )
                        );
                yield diagnosticTest.price();
            }
        };
    }

    /*
     * ============================================================
     * SETUP CLIENT WRAPPER
     * ============================================================
     */

    private <T> T callSetupItem(
            BillingItemTypes billingItemType,
            Long sourceId,
            SetupSupplier<T> supplier
    ) {
        try {
            T response =
                    supplier.get();

            if (response == null) {
                throw new BadRequestAlertException(
                        "Setup item was not found.",
                        ENTITY_NAME,
                        "fallback.item.notfound"
                );
            }

            return response;

        } catch (FeignException.NotFound exception) {
            throw new BadRequestAlertException(
                    "Setup item was not found for type "
                            + billingItemType
                            + " and ID "
                            + sourceId
                            + ".",
                    ENTITY_NAME,
                    "fallback.item.notfound"
            );

        } catch (FeignException exception) {
            LOG.error(
                    "[SETUP_FALLBACK] Setup item call failed "
                            + "itemType={} sourceId={} "
                            + "status={} response={}",
                    billingItemType,
                    sourceId,
                    exception.status(),
                    exception.contentUTF8(),
                    exception
            );

            throw new BadRequestAlertException(
                    "Unable to retrieve fallback item price from Setup Service.",
                    ENTITY_NAME,
                    "fallback.setup.call.failed"
            );
        }
    }

    /*
     * ============================================================
     * VALIDATIONS
     * ============================================================
     */

    private void validateActive(
            Boolean active,
            BillingItemTypes itemType,
            Long sourceId
    ) {
        if (!Boolean.TRUE.equals(
                active
        )) {
            throw new BadRequestAlertException(
                    "Setup item is inactive for type "
                            + itemType
                            + " and ID "
                            + sourceId
                            + ".",
                    ENTITY_NAME,
                    "fallback.item.inactive"
            );
        }
    }

    private void validateDiagnosticTestType(
            BillingItemTypes billingItemType,
            DiagnosticTestSetupDTO diagnosticTest
    ) {
        if (diagnosticTest.type() == null) {
            throw new BadRequestAlertException(
                    "Diagnostic-test type is missing from Setup.",
                    ENTITY_NAME,
                    "fallback.diagnosticTest.type.missing"
            );
        }

        String setupType =
                diagnosticTest.type()
                        .name()
                        .trim()
                        .toUpperCase();

        String expectedType =
                billingItemType
                        .name()
                        .trim()
                        .toUpperCase();

        if (!setupType.equals(
                expectedType
        )) {
            throw new BadRequestAlertException(
                    "Diagnostic-test type mismatch. "
                            + "Billing item type is "
                            + billingItemType
                            + " but Setup test type is "
                            + diagnosticTest.type()
                            + ".",
                    ENTITY_NAME,
                    "fallback.diagnosticTest.type.mismatch"
            );
        }
    }

    private boolean hasValidPriceListResult(
            BillingPricingResolveResponse response
    ) {
        return response != null
                && response.priceListId() != null
                && response.priceListItemId() != null
                && response.unitPrice() != null
                && response.unitPrice().signum() > 0
                && response.currency() != null;
    }

    private void validateStrictPriceListResponse(
            BillingPricingResolveResponse response
    ) {
        if (response == null) {
            throw new BadRequestAlertException(
                    "Setup Service returned no pricing data.",
                    ENTITY_NAME,
                    "response.empty"
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

        if (response.pricingSource() == null) {
            throw new BadRequestAlertException(
                    "Resolved pricing source is missing.",
                    ENTITY_NAME,
                    "pricingSource.missing"
            );
        }

        boolean priceListSource =
                response.pricingSource()
                        == PricingSource.PRICE_LIST
                        || response.pricingSource()
                        == PricingSource.INSURANCE_PRICE_LIST
                        || response.pricingSource()
                        == PricingSource.CASH_PRICE_LIST;

        if (priceListSource
                && response.priceListId() == null) {
            throw new BadRequestAlertException(
                    "Resolved price-list ID is missing.",
                    ENTITY_NAME,
                    "priceListId.missing"
            );
        }

        if (priceListSource
                && response.priceListItemId() == null) {
            throw new BadRequestAlertException(
                    "Resolved price-list item ID is missing.",
                    ENTITY_NAME,
                    "priceListItemId.missing"
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

        if (request.pricingDate() == null) {
            throw new BadRequestAlertException(
                    "Pricing date is required.",
                    ENTITY_NAME,
                    "pricingDate.required"
            );
        }
    }

    /*
     * ============================================================
     * CURRENCY
     * ============================================================
     */

    private Currency parseCurrency(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            throw new BadRequestAlertException(
                    "Brand medication currency is missing.",
                    ENTITY_NAME,
                    "fallback.currency.missing"
            );
        }

        try {
            return Currency.valueOf(
                    value.trim()
                            .toUpperCase()
            );

        } catch (IllegalArgumentException exception) {
            throw new BadRequestAlertException(
                    "Invalid brand medication currency: "
                            + value,
                    ENTITY_NAME,
                    "fallback.currency.invalid"
            );
        }
    }

    @FunctionalInterface
    private interface SetupSupplier<T> {

        T get();
    }
}