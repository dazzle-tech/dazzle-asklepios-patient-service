package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.ClaimEncounterCopy;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Validates the source of a claim encounter copy test result/report.
 * A result belongs to a diagnostic order test, or to a laboratory/radiology line
 * ordered from the Service and Products medical sheet - never both.
 */
@Component
@RequiredArgsConstructor
public class ClaimEncounterCopyServiceProductTestResolver {

    private final PatientServiceAndProductRepository patientServiceAndProductRepository;

    public void requireSingleSource(
            Long orderTestId,
            Long patientServiceProductId,
            String entityName
    ) {
        if ((orderTestId == null) == (patientServiceProductId == null)) {
            throw new BadRequestAlertException(
                    "Exactly one of orderTestId or patientServiceProductId is required.",
                    entityName,
                    "source_required"
            );
        }
    }

    public PatientServiceAndProduct requireServiceProductTest(
            Long patientServiceProductId,
            ClaimEncounterCopy copy,
            BillingItemTypes expectedType,
            String entityName
    ) {
        PatientServiceAndProduct serviceProduct = patientServiceAndProductRepository
                .findById(patientServiceProductId)
                .orElseThrow(() -> new BadRequestAlertException(
                        "PatientServiceAndProduct not found with id " + patientServiceProductId,
                        entityName,
                        "notfound"
                ));

        String typeName = expectedType.name().toLowerCase(Locale.ROOT);

        if (serviceProduct.getBillingItemType() != expectedType
                || serviceProduct.getDiagnosticTestId() == null) {
            throw new BadRequestAlertException(
                    "PatientServiceAndProduct " + patientServiceProductId + " is not a " + typeName + " test",
                    entityName,
                    "not_" + typeName + "_test"
            );
        }

        if (!serviceProduct.getEncounterId().equals(copy.getEncounterId())) {
            throw new BadRequestAlertException(
                    "PatientServiceAndProduct " + patientServiceProductId + " does not belong to this encounter",
                    entityName,
                    "encounter_mismatch"
            );
        }

        return serviceProduct;
    }
}
