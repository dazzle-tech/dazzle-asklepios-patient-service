package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BillingItemDisplayNameService {

    private static final Logger LOG =
            LoggerFactory.getLogger(BillingItemDisplayNameService.class);

    private static final Pattern TECHNICAL_LABEL =
            Pattern.compile("^[A-Z][A-Z0-9_]*\\s*-\\s*\\d+$");

    private final ServiceClient serviceClient;
    private final BrandMedicationClient brandMedicationClient;
    private final ProcedureClient procedureClient;
    private final DiagnosticTestClient diagnosticTestClient;

    public String resolveDisplayName(
            BillingItemTypes billingItemType,
            PatientServiceAndProduct item,
            Long sourceId,
            String storedDescription,
            Map<String, String> cache
    ) {
        if (isHumanReadable(storedDescription, billingItemType)) {
            return storedDescription.trim();
        }

        if (billingItemType == null) {
            return fallbackLabel(storedDescription);
        }

        Long resolvedSourceId = resolveSourceId(item, sourceId);
        if (resolvedSourceId == null) {
            return fallbackLabel(storedDescription);
        }

        String cacheKey =
                billingItemType.name() + ":" + resolvedSourceId;

        return cache.computeIfAbsent(
                cacheKey,
                ignored -> lookupSetupName(
                        billingItemType,
                        item,
                        resolvedSourceId,
                        storedDescription
                )
        );
    }

    private String lookupSetupName(
            BillingItemTypes billingItemType,
            PatientServiceAndProduct item,
            Long sourceId,
            String storedDescription
    ) {
        try {
            return switch (billingItemType) {
                case SERVICE -> readName(
                        serviceClient
                                .getServiceDetails(
                                        pickId(
                                                item == null
                                                        ? null
                                                        : item.getServiceId(),
                                                sourceId
                                        )
                                )
                                .name()
                );
                case MEDICATION -> readName(
                        brandMedicationClient
                                .getBrandMedication(
                                        pickId(
                                                item == null
                                                        ? null
                                                        : item.getBrandMedicationId(),
                                                sourceId
                                        )
                                )
                                .name()
                );
                case PROCEDURE -> readName(
                        procedureClient
                                .getProcedure(
                                        pickId(
                                                item == null
                                                        ? null
                                                        : item.getProcedureId(),
                                                sourceId
                                        )
                                )
                                .name()
                );
                case LABORATORY, RADIOLOGY, PATHOLOGY -> readName(
                        diagnosticTestClient
                                .getDiagnosticTest(
                                        pickId(
                                                item == null
                                                        ? null
                                                        : item.getDiagnosticTestId(),
                                                sourceId
                                        )
                                )
                                .name()
                );
            };
        } catch (FeignException ex) {
            LOG.debug(
                    "Unable to resolve setup display name. type={}, sourceId={}",
                    billingItemType,
                    sourceId,
                    ex
            );
            return fallbackLabel(storedDescription);
        } catch (RuntimeException ex) {
            LOG.debug(
                    "Unable to resolve setup display name. type={}, sourceId={}",
                    billingItemType,
                    sourceId,
                    ex
            );
            return fallbackLabel(storedDescription);
        }
    }

    private Long pickId(Long preferred, Long fallback) {
        return preferred != null ? preferred : fallback;
    }

    private Long resolveSourceId(
            PatientServiceAndProduct item,
            Long sourceId
    ) {
        if (sourceId != null) {
            return sourceId;
        }

        if (item == null) {
            return null;
        }

        if (item.getServiceId() != null) {
            return item.getServiceId();
        }

        if (item.getBrandMedicationId() != null) {
            return item.getBrandMedicationId();
        }

        if (item.getProcedureId() != null) {
            return item.getProcedureId();
        }

        if (item.getDiagnosticTestId() != null) {
            return item.getDiagnosticTestId();
        }

        return item.getSourceId();
    }

    private boolean isHumanReadable(
            String value,
            BillingItemTypes billingItemType
    ) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String upper = value.trim().toUpperCase();

        if (billingItemType != null
                && upper.equals(billingItemType.name())) {
            return false;
        }

        if (upper.equals("SERVICE") || upper.equals("CONSULTATION")) {
            return false;
        }

        return !TECHNICAL_LABEL.matcher(upper).matches();
    }

    private String readName(String name) {
        if (name == null || name.isBlank()) {
            return "-";
        }

        return name.trim();
    }

    private String fallbackLabel(String storedDescription) {
        if (storedDescription != null
                && !storedDescription.isBlank()) {
            return storedDescription.trim();
        }

        return "-";
    }
}
