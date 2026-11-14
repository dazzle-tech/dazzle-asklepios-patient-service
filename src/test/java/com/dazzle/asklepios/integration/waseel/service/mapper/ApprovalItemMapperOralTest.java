package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.client.setup.PriceListSetupItemClient;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.integration.waseel.client.WaseelItemMappingClient;
import com.dazzle.asklepios.integration.waseel.client.dto.WaseelItemMappingSetupDTO;
import com.dazzle.asklepios.integration.waseel.dto.approval.WaseelApprovalItem;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientPrescriptionMedicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApprovalItemMapperOralTest {

    @Mock private WaseelItemMappingClient waseelItemMappingClient;
    @Mock private PriceListSetupItemClient priceListSetupItemClient;
    @Mock private PatientPrescriptionMedicationRepository patientPrescriptionMedicationRepository;
    @Mock private PatientDiagnosisRepository patientDiagnosisRepository;
    @Mock private MedicationDaysSupplyResolver medicationDaysSupplyResolver;
    @Mock private DentalSiteResolver dentalSiteResolver;

    @InjectMocks
    private ApprovalItemMapper mapper;

    private PatientServiceAndProduct dentalProcedure;
    private PatientServiceAndProduct consultation;

    @BeforeEach
    void setUp() {
        when(waseelItemMappingClient.getMappingByItem(anyString(), anyLong())).thenReturn(new WaseelItemMappingSetupDTO(
                1L, "PROCEDURE", 2534L, null, null, null, "PROCEDURES", "97511-00-00", "Restoration", true, null));
        when(dentalSiteResolver.resolve(org.mockito.ArgumentMatchers.any())).thenReturn(new DentalSite("16", "O"));

        dentalProcedure = line(1496L, BillingItemTypes.PROCEDURE, ServiceSource.DENTAL_PROCEDURE);
        dentalProcedure.setProcedureId(2534L);
        consultation = line(1495L, BillingItemTypes.SERVICE, ServiceSource.ENCOUNTER_DEFAULT_SERVICE);
        consultation.setServiceId(29272L);
    }

    @Test
    void dentalTreatmentInADentalClaimIsOralHealthWithItsTooth() {
        List<WaseelApprovalItem> items = mapper.toWaseelItems(
                List.of(dentalProcedure, consultation), BigDecimal.ZERO, null, List.of(), false, true);

        assertThat(items.get(0).type()).isEqualTo("ORAL-HEALTH-OP");
        assertThat(items.get(0).bodySite()).isEqualTo("16");
        assertThat(items.get(0).subSite()).isEqualTo("O");
        assertThat(items.get(1).type()).isEqualTo("SERVICES");
        assertThat(items.get(1).bodySite()).isNull();
    }

    @Test
    void otherClaimsKeepTheirItemTypesAndReadNoTooth() {
        List<WaseelApprovalItem> items = mapper.toWaseelItems(
                List.of(dentalProcedure), BigDecimal.ZERO, null, List.of(), false, false);

        assertThat(items.get(0).type()).isEqualTo("PROCEDURES");
        assertThat(items.get(0).bodySite()).isNull();
        verify(dentalSiteResolver, never()).resolve(org.mockito.ArgumentMatchers.any());
    }

    private static PatientServiceAndProduct line(Long id, BillingItemTypes type, ServiceSource source) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .billingItemType(type)
                .serviceSource(source)
                .quantity(1L)
                .unitPrice(new BigDecimal("600"))
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .isBilled(false)
                .build();
        line.setId(id);
        return line;
    }
}
