package com.dazzle.asklepios.service.eligibility;

import com.dazzle.asklepios.domain.BillingChargeLine;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.CoverageStatus;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.ServiceSource;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.client.setup.ServiceClient;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.integration.waseel.event.EncounterPreAuthorizationSyncEvent;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceCoverageRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.BillingPricingService;
import com.dazzle.asklepios.service.CoverageContractShareService;
import com.dazzle.asklepios.service.InsuranceBenefitRuleService;
import com.dazzle.asklepios.service.InsuranceCalculationServiceImpl;
import com.dazzle.asklepios.service.InsurancePatientShareCalculator;
import com.dazzle.asklepios.service.VisitMaxLimitTracker;
import com.dazzle.asklepios.service.dto.InsuranceBenefitRule;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.helper.PayorHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Waseel insurance, copayment 20%, visit max limit 100. Uses the real share calculator, so the hold and the
 * billed split are read from the same numbers.
 */
class VisitMaxLimitPreAuthorizationTest {

    private static final Long ENCOUNTER_ID = 12L;
    private static final Long INSURANCE_ID = 8L;

    private final PatientServiceAndProductRepository lineRepository = mock(PatientServiceAndProductRepository.class);
    private final PatientInsuranceRepository insuranceRepository = mock(PatientInsuranceRepository.class);
    private final BillingChargeLineRepository chargeLineRepository = mock(BillingChargeLineRepository.class);
    private final InsuranceBenefitRuleService benefitRuleService = mock(InsuranceBenefitRuleService.class);
    private final CoverageContractShareService contractShareService = mock(CoverageContractShareService.class);
    private final PayorHelper payorHelper = mock(PayorHelper.class);
    private final BillingPricingService pricingService = mock(BillingPricingService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PayorDTO payor = mock(PayorDTO.class);

    private final List<PatientServiceAndProduct> visitLines = new ArrayList<>();
    private PatientInsurance insurance;
    private InsurancePatientShareCalculator calculator;
    private EligibilityContractApprovalService service;
    private long nextId = 1L;

    @BeforeEach
    void setUp() {
        insurance = PatientInsurance.builder()
                .id(INSURANCE_ID)
                .payorId(5L)
                .inforce("true")
                .maxLimit(new BigDecimal("100"))
                .expirationDate(LocalDate.now().plusYears(1))
                .build();

        PatientInsuranceCoverageRepository manualCoverage = mock(PatientInsuranceCoverageRepository.class);
        when(manualCoverage.findFirstByInsurance_IdAndItemType(any(), any())).thenReturn(Optional.empty());
        when(payor.isWaseelEnabled()).thenReturn(true);
        when(payorHelper.findPayor(any(), any())).thenReturn(payor);
        when(benefitRuleService.isLatestCoverageInForce(any())).thenReturn(true);
        when(benefitRuleService.getStoredRules(INSURANCE_ID)).thenReturn(List.of(new InsuranceBenefitRule(
                1L, "Outpatient", "Outpatient Services", null, "IN_NETWORK", null, null, null, "SAR",
                null, null, new BigDecimal("20"), null, false, null)));
        when(contractShareService.capWithCoverage(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(3));
        when(lineRepository.findByEncounterId(ENCOUNTER_ID)).thenReturn(visitLines);
        when(insuranceRepository.findById(INSURANCE_ID)).thenReturn(Optional.of(insurance));
        when(chargeLineRepository.findAllByPatientServiceProduct_IdInAndStatusNotInOrderByIdAsc(any(), any()))
                .thenReturn(List.of());
        when(pricingService.patientPayableRatio(any(), any(), any())).thenReturn(BigDecimal.ONE);

        EligibilityContractReader reader = new EligibilityContractReader(benefitRuleService);
        calculator = new InsurancePatientShareCalculator(
                benefitRuleService,
                new InsuranceCalculationServiceImpl(),
                manualCoverage,
                lineRepository,
                mock(ServiceClient.class),
                payorHelper,
                contractShareService,
                reader
        );
        service = new EligibilityContractApprovalService(
                reader,
                calculator,
                contractShareService,
                mock(PatientEncounterRepository.class),
                insuranceRepository,
                lineRepository,
                chargeLineRepository,
                pricingService,
                eventPublisher
        );
    }

    @Test
    void linesWithinTheLimitAreBilledAsBefore() {
        assertThat(order(300)).isFalse();
        assertThat(order(100)).isFalse();

        assertThat(patientShares()).containsExactly("60.00", "20.00");
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void theLineThatReachesTheLimitExactlyIsBilled() {
        order(300);

        assertThat(order(200)).isFalse();
        assertThat(patientShares()).containsExactly("60.00", "40.00");
    }

    @Test
    void theLineThatCrossesTheLimitIsHeldWhole() {
        order(300);

        assertThat(order(250)).isTrue();

        PatientServiceAndProduct held = visitLines.get(1);
        assertThat(held.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(held.getPreAuthorizationRequired()).isTrue();
        assertThat(held.getPaymentStatus()).isEqualTo(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        assertThat(held.getInsuranceShareAmount()).isNull();
        assertThat(held.getNetAmount()).isEqualByComparingTo("250");
        verify(eventPublisher).publishEvent((Object) new EncounterPreAuthorizationSyncEvent(ENCOUNTER_ID));
    }

    @Test
    void everyLineAfterTheLimitIsUsedUpIsHeld() {
        order(500);

        assertThat(order(10)).isTrue();
        assertThat(order(1000)).isTrue();
    }

    @Test
    void aFullyCoveredLineIsNotHeldAfterTheLimitIsUsedUp() {
        order(500);
        when(contractShareService.capWithCoverage(any(), any(), any(), any()))
                .thenAnswer(call -> new InsuranceSplit(BigDecimal.ZERO, call.getArgument(2)));

        assertThat(order(80)).isFalse();
    }

    @Test
    void anApprovedLineIsBilledWithWhatIsLeftOfTheLimit() {
        order(300);
        order(250);
        PatientServiceAndProduct approved = visitLines.get(1);
        approved.setPreAuthorizationStatus(PreAuthorizationStatus.APPROVED);
        approved.setPreAuthorizationRequired(false);
        approved.setPreAuthorizationRequestId(77L);
        approved.setPaymentStatus(PaymentStatus.PENDING);

        assertThat(service.holdIfExceeded(context(approved, "250"))).isFalse();
        InsuranceSplit split = calculator.calculateSplit(insurance, approved, new BigDecimal("250"));
        assertThat(split.patientShare()).isEqualByComparingTo("40");
        assertThat(split.insuranceShare()).isEqualByComparingTo("210");
    }

    @Test
    void aRejectedLinePaidAsCashDoesNotUseTheLimit() {
        order(300);
        PatientServiceAndProduct rejected = line();
        rejected.setPreAuthorizationStatus(PreAuthorizationStatus.REJECTED);
        rejected.setPatientShareAmount(new BigDecimal("250"));
        visitLines.add(rejected);

        assertThat(order(200)).isFalse();
        assertThat(patientShares()).last().isEqualTo("40.00");
    }

    @Test
    void cancelledAndOtherInsuranceLinesDoNotUseTheLimit() {
        order(500);
        visitLines.get(0).setPaymentStatus(PaymentStatus.CANCELLED);
        PatientServiceAndProduct other = line();
        other.setPatientInsuranceId(99L);
        other.setPatientShareAmount(new BigDecimal("100"));
        visitLines.add(other);

        assertThat(order(400)).isFalse();
    }

    @Test
    void noMaxLimitMeansNoHold() {
        insurance.setMaxLimit(null);

        assertThat(order(5000)).isFalse();
        assertThat(order(5000)).isFalse();
    }

    @Test
    void anNphiesContractKeepsItsOwnFlow() {
        when(contractShareService.usesNphiesApprovalCoverage(any(), any())).thenReturn(true);
        PatientServiceAndProduct line = line();
        visitLines.add(line);

        assertThat(service.holdIfExceeded(context(line, "5000"))).isFalse();
    }

    @Test
    void aPayerOutsideWaseelIsNotHeld() {
        when(payor.isWaseelEnabled()).thenReturn(false);
        PatientServiceAndProduct line = line();
        visitLines.add(line);

        assertThat(service.holdIfExceeded(context(line, "5000"))).isFalse();
    }

    @Test
    void cashExemptAndVaccineLinesAreNotHeld() {
        order(500);
        PatientServiceAndProduct cash = line();
        cash.setCoverageStatus(CoverageStatus.NOT_COVERED);
        PatientServiceAndProduct exempt = line();
        exempt.setIsExempted(true);
        PatientServiceAndProduct vaccine = line();
        vaccine.setBillingItemType(BillingItemTypes.VACCINE);

        assertThat(service.holdIfExceeded(context(cash, "300"))).isFalse();
        assertThat(service.holdIfExceeded(context(exempt, "300"))).isFalse();
        assertThat(service.holdIfExceeded(context(vaccine, "300"))).isFalse();
    }

    @Test
    void theSyncKeepsAHeldLineEvenWhenTheLimitHasRoomAgain() {
        order(500);
        order(250);
        PatientServiceAndProduct held = visitLines.get(1);
        visitLines.get(0).setPaymentStatus(PaymentStatus.CANCELLED);

        assertThat(service.requiresPreAuthorization(held, visitLines)).isTrue();
    }

    @Test
    void theSyncNeverHoldsALineAlreadyOnThePatientAccount() {
        order(500);
        order(250);
        PatientServiceAndProduct charged = visitLines.get(0);
        PatientServiceAndProduct overLimit = line();
        overLimit.setNetAmount(new BigDecimal("300"));
        overLimit.setPatientShareAmount(BigDecimal.ZERO);
        overLimit.setInsuranceShareAmount(new BigDecimal("300"));
        visitLines.add(overLimit);

        assertThat(service.requiresPreAuthorization(charged, visitLines)).isFalse();
        assertThat(service.requiresPreAuthorization(overLimit, visitLines)).isFalse();

        PatientServiceAndProduct held = visitLines.get(1);
        when(chargeLineRepository.findAllByPatientServiceProduct_IdInAndStatusNotInOrderByIdAsc(any(), any()))
                .thenReturn(List.of(new BillingChargeLine()));
        assertThat(service.requiresPreAuthorization(held, visitLines)).isFalse();
    }

    @Test
    void thePreviewReadsTheRunningLimit() {
        VisitMaxLimitTracker tracker = calculator.createVisitMaxLimitTracker(insurance);
        PatientServiceAndProduct preview = line();
        preview.setId(null);

        assertThat(service.requiresPreAuthorization(insurance, preview, new BigDecimal("300"), tracker, BigDecimal.ONE)).isFalse();
        calculator.calculateSplit(insurance, preview, new BigDecimal("300"), tracker, BigDecimal.ONE);
        assertThat(service.requiresPreAuthorization(insurance, preview, new BigDecimal("250"), tracker, BigDecimal.ONE)).isTrue();
        assertThat(service.requiresPreAuthorization(insurance, preview, new BigDecimal("200"), tracker, BigDecimal.ONE)).isFalse();
        assertThat(tracker.remaining()).isEqualByComparingTo("40");
    }

    @Test
    void theShareWithoutItsTaxIsCompared() {
        when(pricingService.patientPayableRatio(any(), any(), any())).thenReturn(new BigDecimal("0.869565"));

        assertThat(order(575)).isFalse();
    }

    @Test
    void raisingAChargedLineAboveTheLimitIsReported() {
        order(300);
        PatientServiceAndProduct charged = visitLines.get(0);

        charged.setNetAmount(new BigDecimal("500"));
        assertThat(service.repricingCrossesVisitMaxLimit(charged)).isFalse();
        charged.setNetAmount(new BigDecimal("600"));
        assertThat(service.repricingCrossesVisitMaxLimit(charged)).isTrue();
        charged.setPreAuthorizationStatus(PreAuthorizationStatus.APPROVED);
        charged.setPreAuthorizationRequestId(77L);
        assertThat(service.repricingCrossesVisitMaxLimit(charged)).isFalse();
    }

    @Test
    void aRequestLeavesItsOwnLinesOutOfWhatTheVisitUsed() {
        order(300);
        order(100);

        assertThat(calculator.encounterVisitMaxLimitTracker(insurance, ENCOUNTER_ID, java.util.Set.of()).remaining())
                .isEqualByComparingTo("20");
        assertThat(calculator.encounterVisitMaxLimitTracker(insurance, ENCOUNTER_ID, java.util.Set.of(2L)).remaining())
                .isEqualByComparingTo("40");
    }

    /** Orders a line as billing does: hold it, or split it on the account. Returns whether it was held. */
    private boolean order(int net) {
        PatientServiceAndProduct line = line();
        visitLines.add(line);
        BigDecimal amount = BigDecimal.valueOf(net);
        if (service.holdIfExceeded(context(line, amount.toPlainString()))) {
            return true;
        }
        InsuranceSplit split = calculator.calculateSplit(
                insurance, line, amount, null, pricingService.patientPayableRatio(null, null, null));
        line.setNetAmount(amount);
        line.setPatientShareAmount(split.patientShare());
        line.setInsuranceShareAmount(split.insuranceShare());
        return false;
    }

    private List<String> patientShares() {
        return visitLines.stream().map(line -> line.getPatientShareAmount().setScale(2).toPlainString()).toList();
    }

    private PatientServiceAndProduct line() {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .encounterId(ENCOUNTER_ID)
                .billingItemType(BillingItemTypes.PROCEDURE)
                .serviceSource(ServiceSource.PROCEDURE)
                .patientInsuranceId(INSURANCE_ID)
                .coverageStatus(CoverageStatus.COVERED)
                .preAuthorizationStatus(PreAuthorizationStatus.NOT_REQUIRED)
                .paymentStatus(PaymentStatus.PENDING)
                .isBilled(false)
                .isExempted(false)
                .build();
        line.setId(nextId++);
        line.setInsuranceShareAmount(null);
        return line;
    }

    private static BillingProcessingContext context(PatientServiceAndProduct line, String net) {
        BigDecimal amount = new BigDecimal(net);
        return BillingProcessingContext.builder()
                .patientServiceProduct(line)
                .pricingResult(new PriceCalculationResult(
                        BigDecimal.ONE, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, amount))
                .build();
    }
}
