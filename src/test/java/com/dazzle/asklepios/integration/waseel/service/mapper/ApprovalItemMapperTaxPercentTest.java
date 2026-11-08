package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PriceListSetupItemClient;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ApprovalItemMapperTaxPercentTest {

    @Mock private WaseelItemMappingClient waseelItemMappingClient;
    @Mock private PriceListSetupItemClient priceListSetupItemClient;
    @Mock private PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    @Mock private PatientDiagnosisRepository patientDiagnosisRepository;
    @Mock private MedicationDaysSupplyResolver medicationDaysSupplyResolver;

    @InjectMocks
    private ApprovalItemMapper mapper;

    @Test
    void claimItemCarriesTheAppliedTaxPercent() {
        WaseelApprovalItem item = invoiced(new BigDecimal("10.80"), new BigDecimal("15.000000"), "82.80");

        assertThat(item.taxPercent()).isEqualByComparingTo("15.00");
        assertThat(item.taxPercent().scale()).isEqualTo(2);
        assertThat(item.tax()).isEqualByComparingTo("10.80");
        assertThat(item.net()).isEqualByComparingTo("82.80");
    }

    @Test
    void lineWithoutTaxSendsZeroPercentEvenWhenARateExists() {
        WaseelApprovalItem item = invoiced(BigDecimal.ZERO, new BigDecimal("15.000000"), "72.00");

        assertThat(item.taxPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void lineWithoutARateSendsZeroPercent() {
        WaseelApprovalItem item = invoiced(new BigDecimal("10.80"), null, "82.80");

        assertThat(item.taxPercent()).isEqualByComparingTo("0.00");
    }

    private WaseelApprovalItem invoiced(BigDecimal tax, BigDecimal taxPercent, String net) {
        WaseelApprovalItem base = new WaseelApprovalItem(
                1, "PROCEDURES", "83600-00-10", null, null, null, false, false, null, null,
                1, null, new BigDecimal("72.00"), BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, null, null, List.of(), List.of(1), List.of(1), null, List.of()
        );
        return mapper.withInvoiceAmounts(
                base,
                "INV-1",
                new BigDecimal("72.00"),
                new BigDecimal("72.00"),
                BigDecimal.ZERO,
                tax,
                taxPercent,
                new BigDecimal(net),
                BigDecimal.ZERO,
                new BigDecimal(net),
                1
        );
    }
}
