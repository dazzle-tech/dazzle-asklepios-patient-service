package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.BrandMedicationClient;
import com.dazzle.asklepios.client.setup.DiagnosticTestClient;
import com.dazzle.asklepios.client.setup.ProcedureClient;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.BrandMedicationSetupDTO;
import com.dazzle.asklepios.client.setup.dto.DiagnosticTestSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ProcedureSetupDTO;
import com.dazzle.asklepios.client.setup.dto.ServiceSetupDTO;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.service.dto.billing.BillingRuleEvaluationRequest;
import com.dazzle.asklepios.service.dto.patientServiceProduct.PatientServiceProductCreateDTO;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogBillingRuleLookupService {

    private static final Logger LOG =
            LoggerFactory.getLogger(
                    CatalogBillingRuleLookupService.class
            );

    private static final String ENTITY_NAME =
            "catalogBillingRuleLookup";

    private final ServiceClient serviceClient;
    private final ProcedureClient procedureClient;
    private final DiagnosticTestClient diagnosticTestClient;
    private final BrandMedicationClient brandMedicationClient;

    public Long lookupBillingRuleId(
            PatientServiceAndProduct item
    ) {
        if (item == null || item.getBillingItemType() == null) {
            return null;
        }

        return lookupBillingRuleId(
                item.getBillingItemType(),
                item.getServiceId(),
                item.getProcedureId(),
                item.getDiagnosticTestId(),
                item.getBrandMedicationId()
        );
    }

    public Long lookupBillingRuleId(
            PatientServiceProductCreateDTO dto
    ) {
        if (dto == null || dto.billingItemType() == null) {
            return null;
        }

        return lookupBillingRuleId(
                dto.billingItemType(),
                dto.serviceId(),
                dto.procedureId(),
                dto.diagnosticTestId(),
                dto.brandMedicationId()
        );
    }

    public Long lookupBillingRuleId(
            BillingRuleEvaluationRequest request
    ) {
        if (request == null || request.billingItemType() == null) {
            return null;
        }

        return lookupBillingRuleId(
                request.billingItemType(),
                request.serviceId(),
                request.procedureId(),
                request.diagnosticTestId(),
                request.brandMedicationId()
        );
    }

    public Long lookupBillingRuleId(
            BillingItemTypes billingItemType,
            Long serviceId,
            Long procedureId,
            Long diagnosticTestId,
            Long brandMedicationId
    ) {
        try {
            return switch (billingItemType) {
                case SERVICE -> readServiceRuleId(serviceId);
                case PROCEDURE -> readProcedureRuleId(procedureId);
                case LABORATORY,
                     RADIOLOGY,
                     PATHOLOGY -> readDiagnosticTestRuleId(
                        diagnosticTestId
                );
                case MEDICATION -> readBrandMedicationRuleId(
                        brandMedicationId
                );
            };
        } catch (FeignException exception) {
            LOG.warn(
                    "[CATALOG_RULE_LOOKUP] Setup lookup failed itemType={} status={}",
                    billingItemType,
                    exception.status(),
                    exception
            );

            return null;
        }
    }

    private Long readServiceRuleId(Long serviceId) {
        if (serviceId == null) {
            throw missingCatalogReference("Service");
        }

        ServiceSetupDTO service =
                serviceClient.getServiceDetails(serviceId);

        return service == null ? null : service.billingRuleId();
    }

    private Long readProcedureRuleId(Long procedureId) {
        if (procedureId == null) {
            throw missingCatalogReference("Procedure");
        }

        ProcedureSetupDTO procedure =
                procedureClient.getProcedure(procedureId);

        return procedure == null ? null : procedure.billingRuleId();
    }

    private Long readDiagnosticTestRuleId(Long diagnosticTestId) {
        if (diagnosticTestId == null) {
            throw missingCatalogReference("Diagnostic test");
        }

        DiagnosticTestSetupDTO diagnosticTest =
                diagnosticTestClient.getDiagnosticTest(
                        diagnosticTestId
                );

        return diagnosticTest == null
                ? null
                : diagnosticTest.billingRuleId();
    }

    private Long readBrandMedicationRuleId(Long brandMedicationId) {
        if (brandMedicationId == null) {
            throw missingCatalogReference("Medication");
        }

        BrandMedicationSetupDTO brandMedication =
                brandMedicationClient.getBrandMedication(
                        brandMedicationId
                );

        return brandMedication == null
                ? null
                : brandMedication.billingRuleId();
    }

    private BadRequestAlertException missingCatalogReference(
            String label
    ) {
        return new BadRequestAlertException(
                label + " reference is required.",
                ENTITY_NAME,
                "catalogReference.required"
        );
    }
}
