package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.CoverageContractClient;
import com.dazzle.asklepios.client.setup.PayorClient;
import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.service.dto.InsuranceSplit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoverageContractShareServiceTest {

    @Mock
    private CoverageContractClient coverageContractClient;

    @Mock
    private PayorClient payorClient;

    @Mock
    private InsuranceCalculationService insuranceCalculationService;

    @Mock
    private PatientEncounterRepository patientEncounterRepository;

    @Mock
    private PatientDiagnosisRepository patientDiagnosisRepository;

    @Mock
    private PatientServiceAndProductRepository patientServiceAndProductRepository;

    @InjectMocks
    private CoverageContractShareService coverageContractShareService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(patientEncounterRepository.findById(any())).thenReturn(Optional.empty());
        when(patientDiagnosisRepository.findByEncounterId(any())).thenReturn(List.of());
        when(patientServiceAndProductRepository.findByEncounterId(any())).thenReturn(List.of());
        when(patientServiceAndProductRepository.findByPatientAndEncounterDate(any(), any())).thenReturn(List.of());
    }

    @Test
    void calculateSplit_withoutCoverages_usesExistingPercentageFormula() {
        PatientInsurance insurance = insurance();
        CoverageContractResolveDtos.Response response = new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                false,
                false,
                null
        );
        InsuranceSplit expected = new InsuranceSplit(new BigDecimal("150.0000"), new BigDecimal("850.0000"));

        when(coverageContractClient.resolve(any())).thenReturn(response);
        when(insuranceCalculationService.calculateSplit(any(), any(), any())).thenReturn(expected);

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance,
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).contains(expected);
        verify(insuranceCalculationService).calculateSplit(any(), any(), any());
    }

    @Test
    void calculateSplit_withoutCoverages_fixedCopaymentCapsAtNet() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("FIXED", "75"),
                false,
                false,
                null
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("75.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("925.0000");
        verify(insuranceCalculationService, never()).calculateSplit(any(), any(), any());
    }

    @Test
    void calculateSplit_appliesCopaymentOnCoveredAmountOnly() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_fullCoverageKeepsExistingCopaymentMath() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "100")
        ));
        InsuranceSplit expected = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(insuranceCalculationService.calculateSplit(any(), any(), any())).thenReturn(expected);

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).contains(expected);
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("1000.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_uncoveredItemIsCash() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "uncovered",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                true,
                null
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
        verify(insuranceCalculationService, never()).calculateSplit(any(), any(), any());
    }

    @Test
    void calculateSplit_uncoveredItemKeepsCashWhenCashLimitIsExceeded() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "uncovered",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                true,
                null,
                null,
                cashLimit("FIXED", "50")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
        verify(insuranceCalculationService, never()).calculateSplit(any(), any(), any());
    }

    @Test
    void capWithCoverage_doesNotChangeWaselWhenNoReading() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                true,
                true,
                null
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split).isEqualTo(waseel);
    }

    @Test
    void capWithCoverage_capsWaselInsuranceShareToCoveredAmount() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                true,
                false,
                coverage("PERCENTAGE", "50")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("500.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("500.0000");
    }

    @Test
    void calculateSplit_limitWithinRemainingKeepsCoverageAndCopaymentMath() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                limit("FIXED", "10000")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_limitCapsInsuranceShareWhenPeriodIsExceeded() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                limit("FIXED", "1000")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));
        when(patientServiceAndProductRepository.findByEncounterId(12L)).thenReturn(List.of(previousItem("900")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("900.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("100.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void capWithCoverage_unmatchedLimitDoesNotChangeWaselSplit() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                false,
                false,
                null,
                null
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split).isEqualTo(waseel);
    }

    @Test
    void capWithCoverage_limitCapsWaselAfterCoverage() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                true,
                false,
                coverage("PERCENTAGE", "50"),
                limit("FIXED", "100")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("900.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("100.0000");
    }

    @Test
    void calculateSplit_cashLimitWithinRemainingKeepsCoverageAndCopaymentMath() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                cashLimit("FIXED", "10000")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_cashLimitTransfersExcessToInsurance() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                cashLimit("FIXED", "100")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("100.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("900.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_cashLimitKeepsExtraCashWhenCoverageLimitCannotTakeExcess() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                limit("FIXED", "1000"),
                cashLimit("FIXED", "50")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));
        when(patientServiceAndProductRepository.findByEncounterId(12L)).thenReturn(List.of(previousItem("900")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("900.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("100.0000");
    }

    @Test
    void calculateSplit_consumedPatientCashReducesRemainingRoom() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                cashLimit("FIXED", "100")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));
        when(patientServiceAndProductRepository.findByEncounterId(12L)).thenReturn(List.of(previousCashItem("80")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("20.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("980.0000");
    }

    @Test
    void capWithCoverage_cashLimitCapsWaselPatientShare() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                false,
                false,
                null,
                null,
                cashLimit("FIXED", "50")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("50.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("950.0000");
    }

    @Test
    void calculateSplit_appliesDiscountBeforeCoverageAndCopayment() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                null,
                discount("PERCENTAGE", "10")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("144.0000"), new BigDecimal("576.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("324.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("576.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("720.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_uncoveredDoesNotApplyDiscountUnlessDiscountOnCash() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "uncovered",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                true,
                null,
                null,
                null,
                discount("PERCENTAGE", "10")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
    }

    @Test
    void calculateSplit_uncoveredAppliesDiscountWhenDiscountOnCash() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "uncovered",
                contract(),
                new CoverageContractResolveDtos.CopaymentSnapshot(
                        31L, "CLINIC", "PERCENTAGE", new BigDecimal("20"), false, true, false
                ),
                true,
                true,
                null,
                null,
                null,
                discount("PERCENTAGE", "10")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("900.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
    }

    @Test
    void capWithCoverage_scalesWaselSplitToDiscountedNet() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                null,
                false,
                false,
                null,
                null,
                null,
                discount("PERCENTAGE", "10")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("180.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("720.0000");
    }

    @Test
    void calculateSplit_excludedItemIsCashEvenWhenCoveragesMatch() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "excluded",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                null,
                null,
                exclusion("YES")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
        verify(insuranceCalculationService, never()).calculateSplit(any(), any(), any());
    }

    @Test
    void calculateSplit_exclusionNoKeepsCoveredSplit() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "exclusion override",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                null,
                null,
                exclusion("NO")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
        verify(insuranceCalculationService).calculateSplit(
                new BigDecimal("800.0000"),
                new BigDecimal("20"),
                BigDecimal.ZERO
        );
    }

    @Test
    void calculateSplit_unmatchedExclusionLeavesCoveredNumbersUnchanged() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80")
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
    }

    @Test
    void calculateSplit_excludedDoesNotApplyDiscountUnlessDiscountOnExcluded() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "excluded",
                contract(),
                copayment("PERCENTAGE", "20"),
                false,
                false,
                null,
                null,
                null,
                discount("PERCENTAGE", "10"),
                exclusion("YES")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
    }

    @Test
    void calculateSplit_excludedAppliesDiscountWhenDiscountOnExcluded() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "excluded",
                contract(),
                new CoverageContractResolveDtos.CopaymentSnapshot(
                        31L, "CLINIC", "PERCENTAGE", new BigDecimal("20"), true, false, false
                ),
                false,
                false,
                null,
                null,
                null,
                discount("PERCENTAGE", "10"),
                exclusion("YES")
        ));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("900.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("0.0000");
    }

    @Test
    void capWithCoverage_excludedWaselItemIsCash() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "excluded",
                contract(),
                copayment("PERCENTAGE", "20"),
                false,
                false,
                null,
                null,
                null,
                null,
                exclusion("YES")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("1000.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("0.0000");
    }

    @Test
    void capWithCoverage_unmatchedExclusionLeavesWaselSplitUnchanged() {
        InsuranceSplit waseel = new InsuranceSplit(new BigDecimal("200.0000"), new BigDecimal("800.0000"));
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20")
        ));

        InsuranceSplit split = coverageContractShareService.capWithCoverage(
                insurance(),
                item(),
                new BigDecimal("1000"),
                waseel
        );

        assertThat(split.patientShare()).isEqualByComparingTo("200.0000");
        assertThat(split.insuranceShare()).isEqualByComparingTo("800.0000");
    }

    @Test
    void calculateSplit_preApprovalDoesNotChangeShareNumbers() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "pre-approval",
                contract(),
                copayment("PERCENTAGE", "20"),
                true,
                false,
                coverage("PERCENTAGE", "80"),
                null,
                null,
                null,
                null,
                preApproval()
        ));
        when(insuranceCalculationService.calculateSplit(any(), any(), any()))
                .thenReturn(new InsuranceSplit(new BigDecimal("160.0000"), new BigDecimal("640.0000")));

        Optional<InsuranceSplit> split = coverageContractShareService.calculateSplit(
                insurance(),
                item(),
                new BigDecimal("1000")
        );

        assertThat(split).isPresent();
        assertThat(split.get().patientShare()).isEqualByComparingTo("360.0000");
        assertThat(split.get().insuranceShare()).isEqualByComparingTo("640.0000");
    }

    @Test
    void requiresPreApproval_whenContractReadingMatches() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "pre-approval",
                contract(),
                copayment("PERCENTAGE", "20"),
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                preApproval()
        ));

        assertThat(coverageContractShareService.requiresPreApproval(insurance(), item())).isTrue();
    }

    @Test
    void requiresPreApproval_unmatchedReadingLeavesExistingFlow() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "matched",
                contract(),
                copayment("PERCENTAGE", "20")
        ));

        assertThat(coverageContractShareService.requiresPreApproval(insurance(), item())).isFalse();
    }

    @Test
    void requiresPreApproval_excludedItemDoesNotRequireApproval() {
        when(coverageContractClient.resolve(any())).thenReturn(new CoverageContractResolveDtos.Response(
                true,
                "excluded",
                contract(),
                copayment("PERCENTAGE", "20"),
                false,
                false,
                null,
                null,
                null,
                null,
                exclusion("YES"),
                preApproval()
        ));

        assertThat(coverageContractShareService.requiresPreApproval(insurance(), item())).isFalse();
    }

    private PatientInsurance insurance() {
        return PatientInsurance.builder()
                .id(8L)
                .payerNphiesId("INS-1")
                .policyNumber("POL-100")
                .policyClassName("A")
                .build();
    }

    private PatientServiceAndProduct item() {
        return PatientServiceAndProduct.builder()
                .id(44L)
                .encounterId(12L)
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceId(90L)
                .build();
    }

    private PatientServiceAndProduct previousItem(String netAmount) {
        return PatientServiceAndProduct.builder()
                .id(10L)
                .encounterId(12L)
                .patientInsuranceId(8L)
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceId(90L)
                .netAmount(new BigDecimal(netAmount))
                .grossAmount(new BigDecimal(netAmount))
                .build();
    }

    private PatientServiceAndProduct previousCashItem(String patientShare) {
        return PatientServiceAndProduct.builder()
                .id(10L)
                .encounterId(12L)
                .patientInsuranceId(8L)
                .billingItemType(BillingItemTypes.SERVICE)
                .serviceId(90L)
                .netAmount(new BigDecimal("1000"))
                .grossAmount(new BigDecimal("1000"))
                .patientShareAmount(new BigDecimal(patientShare))
                .build();
    }

    private CoverageContractResolveDtos.ContractSnapshot contract() {
        return new CoverageContractResolveDtos.ContractSnapshot(
                21L, "INSURANCE", 9L, "Tawuniya", "C-1", "POL-100", "GROSS",
                9L, "Tawuniya", 4L, "PL", null, null, 21L, "A", true
        );
    }

    private CoverageContractResolveDtos.CopaymentSnapshot copayment(String valueType, String amount) {
        return new CoverageContractResolveDtos.CopaymentSnapshot(
                31L,
                "CLINIC",
                valueType,
                new BigDecimal(amount),
                false,
                false,
                false
        );
    }

    private CoverageContractResolveDtos.CoverageReadingSnapshot coverage(String valueType, String amount) {
        return new CoverageContractResolveDtos.CoverageReadingSnapshot(
                11L,
                22L,
                "CATEGORY",
                "SERVICE",
                90L,
                valueType,
                new BigDecimal(amount)
        );
    }

    private CoverageContractResolveDtos.CoverageReadingSnapshot limit(String valueType, String amount) {
        return new CoverageContractResolveDtos.CoverageReadingSnapshot(
                51L,
                52L,
                "CATEGORY",
                "SERVICE",
                90L,
                valueType,
                new BigDecimal(amount),
                "PER_ENCOUNTER",
                "NET"
        );
    }

    private CoverageContractResolveDtos.CoverageReadingSnapshot cashLimit(String valueType, String amount) {
        return new CoverageContractResolveDtos.CoverageReadingSnapshot(
                71L,
                72L,
                "CATEGORY",
                "SERVICE",
                90L,
                valueType,
                new BigDecimal(amount),
                "PER_ENCOUNTER",
                "NET"
        );
    }

    private CoverageContractResolveDtos.DiscountSnapshot discount(String type, String value) {
        return new CoverageContractResolveDtos.DiscountSnapshot(
                81L,
                "SERVICE",
                "SERVICE",
                90L,
                "ALL",
                type,
                new BigDecimal(value)
        );
    }

    private CoverageContractResolveDtos.ExclusionSnapshot exclusion(String result) {
        return new CoverageContractResolveDtos.ExclusionSnapshot(
                91L,
                "SERVICE",
                "SERVICE",
                90L,
                false,
                null,
                "ALL",
                result
        );
    }

    private CoverageContractResolveDtos.PreApprovalSnapshot preApproval() {
        return new CoverageContractResolveDtos.PreApprovalSnapshot(
                11L,
                3L,
                "FACILITY",
                1L,
                null,
                "ALL",
                "SERVICE",
                null,
                90L,
                false,
                null
        );
    }
}
