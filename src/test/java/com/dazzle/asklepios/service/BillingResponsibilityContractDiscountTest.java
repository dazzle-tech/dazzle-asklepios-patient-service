package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.billing.BillingChargeLineStatus;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.BillingChargeResponsibilityRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.VisitMaxLimitTracker;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.integration.waseel.service.PreAuthorizationResolutionService;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PartyTaxDistribution;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillingResponsibilityContractDiscountTest {

    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    @Mock
    private BillingChargeResponsibilityRepository billingChargeResponsibilityRepository;

    @Mock
    private BillingChargeLineRepository billingChargeLineRepository;

    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;

    @Mock
    private PatientServiceAndProductRepository patientServiceAndProductRepository;

    @Mock
    private InsurancePatientShareCalculator insurancePatientShareCalculator;

    @Mock
    private BillingPricingService billingPricingService;

    @Mock
    private ServiceClient serviceClient;

    @Mock
    private PreAuthorizationResolutionService preAuthorizationResolutionService;

    @Mock
    private PreAuthorizationItemRepository preAuthorizationItemRepository;

    @Mock
    private EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    @Mock
    private BillingChargeService billingChargeService;

    @InjectMocks
    private BillingResponsibilityService billingResponsibilityService;

    private final PatientInsurance insurance = PatientInsurance.builder().id(8L).expirationDate(LocalDate.now().plusDays(30)).build();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(patientInsuranceRepository.findById(8L)).thenReturn(Optional.of(insurance));
        when(patientInsuranceRepository.findByIdAndPatient_Id(8L, 3L)).thenReturn(Optional.of(insurance));
        when(insurancePatientShareCalculator.isLatestCoverageInForce(insurance)).thenReturn(true);
        when(billingChargeResponsibilityRepository.saveAndFlush(any())).then(AdditionalAnswers.returnsFirstArg());
        when(billingPricingService.distributeTax(any(), any(), any(), any(), any())).thenAnswer(call ->
                new PartyTaxDistribution(ZERO, ZERO, ZERO, call.getArgument(1), call.getArgument(2))
        );
        when(insurancePatientShareCalculator.calculateSplit(any(), any(), any(), nullable(VisitMaxLimitTracker.class)))
                .thenReturn(new InsuranceSplit(new BigDecimal("40.0000"), new BigDecimal("40.0000")));
    }

    @Test
    void calculate_contractDiscountLowersTheLineAndNeverReachesTheClaim() {
        BillingChargeLine chargeLine = chargeLine("100.0000", "0.0000", "0.0000");

        billingResponsibilityService.calculate(context(chargeLine));

        assertThat(chargeLine.getContractDiscountAmount()).isEqualByComparingTo("20");
        assertThat(chargeLine.getDiscountAmount()).isEqualByComparingTo("20");
        assertThat(chargeLine.getNetAmount()).isEqualByComparingTo("80");
        assertThat(chargeLine.getPatientResponsibilityAmount()).isEqualByComparingTo("40");
        assertThat(chargeLine.getInsuranceResponsibilityAmount()).isEqualByComparingTo("40");
        assertThat(chargeLine.getPatientServiceProduct().getNetAmount()).isEqualByComparingTo("80");
    }

    @Test
    void refresh_startsFromTheFullItemSoTheDiscountIsNotTakenTwice() {
        BillingChargeLine chargeLine = chargeLine("80.0000", "20.0000", "20.0000");
        when(billingChargeLineRepository.findAllByEncounter_IdAndStatusNotInOrderByIdAsc(eq(12L), any()))
                .thenReturn(List.of(chargeLine));

        billingResponsibilityService.refreshInsuranceResponsibilitiesForEncounter(12L);

        ArgumentCaptor<BigDecimal> net = ArgumentCaptor.forClass(BigDecimal.class);
        verify(insurancePatientShareCalculator).calculateSplit(any(), any(), net.capture(), nullable(VisitMaxLimitTracker.class));
        assertThat(net.getValue()).isEqualByComparingTo("100");
        assertThat(chargeLine.getContractDiscountAmount()).isEqualByComparingTo("20");
        assertThat(chargeLine.getDiscountAmount()).isEqualByComparingTo("20");
        assertThat(chargeLine.getNetAmount()).isEqualByComparingTo("80");
    }

    @Test
    void calculate_exemptedLineLeavesNoContractDiscountBehind() {
        BillingChargeLine chargeLine = chargeLine("80.0000", "20.0000", "20.0000");
        chargeLine.getPatientServiceProduct().setIsExempted(true);

        billingResponsibilityService.calculate(context(chargeLine));

        assertThat(chargeLine.getContractDiscountAmount()).isEqualByComparingTo("0");
        assertThat(chargeLine.getDiscountAmount()).isEqualByComparingTo("0");
    }

    private BillingProcessingContext context(BillingChargeLine chargeLine) {
        return BillingProcessingContext.builder()
                .idempotencyKey("KEY")
                .patientServiceProduct(chargeLine.getPatientServiceProduct())
                .chargeLine(chargeLine)
                .pricingResult(new PriceCalculationResult(
                        BigDecimal.ONE,
                        new BigDecimal("100.0000"),
                        new BigDecimal("100.0000"),
                        ZERO,
                        ZERO,
                        new BigDecimal("100.0000"),
                        ZERO,
                        new BigDecimal("100.0000")
                ))
                .build();
    }

    private BillingChargeLine chargeLine(String net, String discount, String contractDiscount) {
        PatientServiceAndProduct item = PatientServiceAndProduct.builder()
                .id(44L)
                .encounterId(12L)
                .patientId(3L)
                .patientInsuranceId(8L)
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceId(90L)
                .build();
        return BillingChargeLine.builder()
                .id(1L)
                .patientServiceProduct(item)
                .status(BillingChargeLineStatus.OPEN)
                .quantity(BigDecimal.ONE)
                .unitPrice(new BigDecimal("100.0000"))
                .grossAmount(new BigDecimal("100.0000"))
                .discountAmount(new BigDecimal(discount))
                .contractDiscountAmount(new BigDecimal(contractDiscount))
                .netAmount(new BigDecimal(net))
                .taxAmount(ZERO)
                .allocatedAmount(ZERO)
                .reservedAmount(ZERO)
                .build();
    }
}
