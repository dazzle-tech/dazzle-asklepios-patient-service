package com.dazzle.asklepios.integration.waseel.service.mapper;

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
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class ApprovalItemMapper {

    private final ProcedureClient procedureClient;
    private final ServiceClient serviceClient;
    private final DiagnosticTestClient diagnosticTestClient;
    private final BrandMedicationClient brandMedicationClient;

    public List<WaseelApprovalItem> toWaseelItems(List<PatientServiceAndProduct> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        AtomicInteger sequence = new AtomicInteger(1);

        return items.stream()
                .filter(item -> Boolean.FALSE.equals(item.getIsBilled()))
                .map(item -> toWaseelItem(item, sequence.getAndIncrement()))
                .toList();
    }

    private WaseelApprovalItem toWaseelItem(PatientServiceAndProduct item, Integer sequence) {
        BillingItemTypes type = item.getBillingItemType();

        if (type == BillingItemTypes.PROCEDURE) {
            ProcedureSetupDTO procedure = getProcedure(item.getProcedureId());

            return buildItem(
                    item,
                    sequence,
                    "procedure",
                    safe(procedure.code()),
                    safe(procedure.name())
            );
        }

        if (type == BillingItemTypes.SERVICE) {
            ServiceSetupDTO service = getService(item.getServiceId());

            return buildItem(
                    item,
                    sequence,
                    "services",
                    safe(service.code()),
                    safe(service.name())
            );
        }

        if (type == BillingItemTypes.LABORATORY
                || type == BillingItemTypes.RADIOLOGY
                || type == BillingItemTypes.PATHOLOGY) {
            DiagnosticTestSetupDTO diagnosticTest = getDiagnosticTest(item.getDiagnosticTestId());

            return buildItem(
                    item,
                    sequence,
                    mapDiagnosticType(type),
                    safe(diagnosticTest.internalCode()),
                    safe(diagnosticTest.name())
            );
        }

        if (type == BillingItemTypes.MEDICATION) {
            BrandMedicationSetupDTO medication = getBrandMedication(item.getBrandMedicationId());

            return buildItem(
                    item,
                    sequence,
                    "medication",
                    safe(medication.code()),
                    safe(medication.name())
            );
        }

        return buildItem(
                item,
                sequence,
                type == null ? "" : type.name().toLowerCase(),
                "",
                ""
        );
    }

    private WaseelApprovalItem buildItem(
            PatientServiceAndProduct item,
            Integer sequence,
            String type,
            String itemCode,
            String itemDescription
    ) {
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity() == null ? 1L : item.getQuantity());
        BigDecimal unitPrice = safe(item.getUnitPrice());
        BigDecimal discount = safe(item.getDiscountAmount());
        BigDecimal tax = safe(item.getTaxAmount());
        BigDecimal net = safe(item.getTotalAmount());

        return new WaseelApprovalItem(
                sequence,
                type,
                itemCode,
                itemDescription,
                "",
                "",
                false,
                false,
                "",
                "",
                quantity,
                "",
                unitPrice,
                discount,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                net,
                tax,
                BigDecimal.ZERO,
                net,
                LocalDate.now(),
                LocalDate.now(),
                List.of(),
                List.of(1),
                List.of(),
                null,
                List.of(),
                item.getNotes()
        );
    }

    private String mapDiagnosticType(BillingItemTypes type) {
        if (type == BillingItemTypes.RADIOLOGY) {
            return "radiology";
        }

        if (type == BillingItemTypes.PATHOLOGY) {
            return "pathology";
        }

        return "laboratory";
    }

    private ProcedureSetupDTO getProcedure(Long procedureId) {
        try {
            return procedureClient.getProcedure(procedureId);
        } catch (FeignException.NotFound ex) {
            return new ProcedureSetupDTO(
                    procedureId,
                    "Procedure not found",
                    "",
                    "",
                    false,
                    "",
                    "",
                    "",
                    "",
                    false,
                    null,
                    null,
                    0L
            );
        }
    }

    private ServiceSetupDTO getService(Long serviceId) {
        try {
            return serviceClient.getServiceDetails(serviceId);
        } catch (FeignException.NotFound ex) {
            return new ServiceSetupDTO(
                    serviceId,
                    "Service not found",
                    "",
                    "",
                    "",
                    0L,
                    null,
                    false,
                    null,
                    false,
                    1,
                    null,
                    0,
                    0
            );
        }
    }

    private DiagnosticTestSetupDTO getDiagnosticTest(Long diagnosticTestId) {
        try {
            return diagnosticTestClient.getDiagnosticTest(diagnosticTestId);
        } catch (FeignException.NotFound ex) {
            return new DiagnosticTestSetupDTO(
                    diagnosticTestId,
                    null,
                    "Diagnostic test not found",
                    "",
                    BigDecimal.ZERO,
                    null,
                    false
            );
        }
    }

    private BrandMedicationSetupDTO getBrandMedication(Long brandMedicationId) {
        try {
            return brandMedicationClient.getBrandMedication(brandMedicationId);
        } catch (FeignException.NotFound ex) {
            return new BrandMedicationSetupDTO(
                    brandMedicationId,
                    "Medication not found",
                    "",
                    BigDecimal.ZERO,
                    "",
                    false
            );
        }
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}