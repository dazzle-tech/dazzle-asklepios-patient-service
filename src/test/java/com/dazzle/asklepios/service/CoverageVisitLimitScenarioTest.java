package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.CoverageContractClient;
import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.PreAuthorizationRequest;
import com.dazzle.asklepios.domain.enumeration.BillingItemTypes;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.PreAuthorizationStatus;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.repository.BillingChargeLineRepository;
import com.dazzle.asklepios.repository.PatientDiagnosisRepository;
import com.dazzle.asklepios.repository.PatientEncounterRepository;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientServiceAndProductRepository;
import com.dazzle.asklepios.repository.PreAuthorizationItemRepository;
import com.dazzle.asklepios.repository.PreAuthorizationRequestRepository;
import com.dazzle.asklepios.service.dto.billing.BillingProcessingContext;
import com.dazzle.asklepios.service.dto.billing.PriceCalculationResult;
import com.dazzle.asklepios.service.helper.PayorHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Visit 455 + radiology 105 with a Coverage Limit of 500. The consultation is outside the limit
 * and the radiology has its own pre-approval, so it is held before the visit total crosses 500.
 */
class CoverageVisitLimitScenarioTest {

    private static final Long ENCOUNTER_ID = 12L;
    private static final Long INSURANCE_ID = 8L;
    private static final Long CONSULTATION_SERVICE_ID = 1L;
    private static final Long HELD_RADIOLOGY_TEST_ID = 187L;

    @Mock
    private CoverageContractClient coverageContractClient;
    @Mock
    private PayorHelper payorHelper;
    @Mock
    private InsuranceCalculationService insuranceCalculationService;
    @Mock
    private PatientEncounterRepository patientEncounterRepository;
    @Mock
    private PatientDiagnosisRepository patientDiagnosisRepository;
    @Mock
    private PatientServiceAndProductRepository patientServiceAndProductRepository;
    @Mock
    private BillingChargeLineRepository billingChargeLineRepository;
    @Mock
    private PatientInsuranceRepository patientInsuranceRepository;
    @Mock
    private PreAuthorizationRequestRepository preAuthorizationRequestRepository;
    @Mock
    private PreAuthorizationItemRepository preAuthorizationItemRepository;
    @Mock
    private EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;

    private final List<PatientServiceAndProduct> visitLines = new ArrayList<>();

    private CoveragePreApprovalLimitService coveragePreApprovalLimitService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        coveragePreApprovalLimitService = new CoveragePreApprovalLimitService(
                new CoverageContractShareService(
                        coverageContractClient,
                        payorHelper,
                        insuranceCalculationService,
                        patientEncounterRepository,
                        patientDiagnosisRepository,
                        patientServiceAndProductRepository,
                        billingChargeLineRepository
                ),
                patientEncounterRepository,
                patientInsuranceRepository,
                patientServiceAndProductRepository,
                preAuthorizationRequestRepository,
                preAuthorizationItemRepository,
                encounterInsuranceEligibilityService
        );

        PatientEncounter encounter = PatientEncounter.builder()
                .id(ENCOUNTER_ID)
                .facilityId(1L)
                .departmentId(5L)
                .build();
        encounter.setPatient(new Patient());
        when(patientEncounterRepository.findById(ENCOUNTER_ID)).thenReturn(Optional.of(encounter));
        when(patientInsuranceRepository.findById(INSURANCE_ID))
                .thenReturn(Optional.of(PatientInsurance.builder().id(INSURANCE_ID).build()));
        when(patientDiagnosisRepository.findByEncounterId(any())).thenReturn(List.of());
        when(patientServiceAndProductRepository.findByEncounterId(ENCOUNTER_ID)).thenReturn(visitLines);
        when(billingChargeLineRepository.findAllByPatientServiceProduct_IdInAndStatusNotInOrderByIdAsc(any(), any()))
                .thenReturn(List.of());
        when(preAuthorizationRequestRepository.findAllById(any())).thenReturn(List.of());
        AtomicLong requestIds = new AtomicLong(100L);
        when(preAuthorizationRequestRepository.save(any())).thenAnswer(call -> {
            PreAuthorizationRequest request = call.getArgument(0);
            request.setId(requestIds.incrementAndGet());
            return request;
        });
        when(coverageContractClient.resolve(any())).thenAnswer(call -> contractFor(call.getArgument(0)));
    }

    @Test
    void heldRadiologyCountsSoTheLastLineOfTheOrderSendsTheWholeVisit() {
        PatientServiceAndProduct consultation = billedLine(1455L, CONSULTATION_SERVICE_ID, BillingItemTypes.SERVICE, "90");
        PatientServiceAndProduct checkUp = billedLine(1456L, 28295L, BillingItemTypes.SERVICE, "270");
        PatientServiceAndProduct radiology = newLine(1457L, 186L, BillingItemTypes.RADIOLOGY);
        PatientServiceAndProduct cbc = newLine(1458L, 138L, BillingItemTypes.LABORATORY);
        PatientServiceAndProduct heldRadiology = newLine(1459L, HELD_RADIOLOGY_TEST_ID, BillingItemTypes.RADIOLOGY);
        PatientServiceAndProduct differential = newLine(1460L, 139L, BillingItemTypes.LABORATORY);

        // The real order was processed as 1457, 1459, 1460, 1458.
        price(radiology, "65");
        assertThat(radiology.getPreAuthorizationRequestId()).isNull();
        radiology.setGrossAmount(new BigDecimal("65"));

        price(heldRadiology, "105");
        assertThat(heldRadiology.getPaymentStatus()).isEqualTo(PaymentStatus.SKIPPED_PENDING_PRE_AUTH);
        assertThat(heldRadiology.getGrossAmount()).isEqualByComparingTo("105");
        Long ownRequest = heldRadiology.getPreAuthorizationRequestId();

        price(differential, "45");
        assertThat(differential.getPreAuthorizationRequestId()).isNull();
        differential.setGrossAmount(new BigDecimal("45"));

        price(cbc, "75");

        assertThat(cbc.getPreAuthorizationRequestId()).isNotNull();
        assertThat(checkUp.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(radiology.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(differential.getPreAuthorizationStatus()).isEqualTo(PreAuthorizationStatus.PENDING_APPROVAL);
        assertThat(heldRadiology.getPreAuthorizationRequestId()).isEqualTo(ownRequest);
        assertThat(consultation.getPreAuthorizationRequestId()).isNull();
    }

    private void price(PatientServiceAndProduct line, String gross) {
        BigDecimal amount = new BigDecimal(gross);
        coveragePreApprovalLimitService.applyIfExceeded(BillingProcessingContext.builder()
                .patientServiceProduct(line)
                .pricingResult(new PriceCalculationResult(
                        BigDecimal.ONE, amount, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO, amount
                ))
                .build());
    }

    private PatientServiceAndProduct billedLine(Long id, Long catalogId, BillingItemTypes type, String gross) {
        PatientServiceAndProduct line = newLine(id, catalogId, type);
        line.setGrossAmount(new BigDecimal(gross));
        line.setNetAmount(new BigDecimal(gross));
        line.setPatientInsuranceId(INSURANCE_ID);
        return line;
    }

    /** Lines of one order are saved together and have no price until they are billed. */
    private PatientServiceAndProduct newLine(Long id, Long catalogId, BillingItemTypes type) {
        PatientServiceAndProduct line = PatientServiceAndProduct.builder()
                .id(id)
                .encounterId(ENCOUNTER_ID)
                .patientInsuranceId(INSURANCE_ID)
                .billingItemType(type)
                .grossAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        if (type == BillingItemTypes.SERVICE) {
            line.setServiceId(catalogId);
        } else {
            line.setDiagnosticTestId(catalogId);
        }
        visitLines.add(line);
        return line;
    }

    private CoverageContractResolveDtos.Response contractFor(CoverageContractResolveDtos.Request request) {
        boolean consultation = CONSULTATION_SERVICE_ID.equals(request.catalogItemId());
        return new CoverageContractResolveDtos.Response(
                true,
                "matched",
                new CoverageContractResolveDtos.ContractSnapshot(
                        21L, "INSURANCE", 9L, "Payer", "C-1", "POL-100", "GROSS",
                        9L, "Payer", 4L, "PL", null, null, 21L, "VIPP-10", true, "NPHIES"
                ),
                null,
                true,
                false,
                null,
                consultation ? null : limit(),
                null,
                null,
                null,
                HELD_RADIOLOGY_TEST_ID.equals(request.catalogItemId()) ? radiologyPreApproval() : null,
                null
        );
    }

    private CoverageContractResolveDtos.CoverageReadingSnapshot limit() {
        return new CoverageContractResolveDtos.CoverageReadingSnapshot(
                1011L, null, "ALL", null, null, "FIXED", new BigDecimal("500"), "PER_ENCOUNTER", "GROSS",
                List.of(new CoverageContractResolveDtos.ExcludedItemSnapshot("SERVICE", CONSULTATION_SERVICE_ID))
        );
    }

    private CoverageContractResolveDtos.PreApprovalSnapshot radiologyPreApproval() {
        return new CoverageContractResolveDtos.PreApprovalSnapshot(
                1003L, 3L, "ITEM", 1L, null, "ALL", "RADIOLOGY", null,
                HELD_RADIOLOGY_TEST_ID, false, null, "FIXED", new BigDecimal("20")
        );
    }
}
