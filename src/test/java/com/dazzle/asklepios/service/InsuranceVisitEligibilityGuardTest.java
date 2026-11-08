package com.dazzle.asklepios.service;

import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.repository.WaseelEligibilityRequestRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InsuranceVisitEligibilityGuardTest {

    @Mock
    private WaseelEligibilityRequestRepository eligibilityRequestRepository;

    @Mock
    private CoverageContractShareService coverageContractShareService;

    @InjectMocks
    private InsuranceVisitEligibilityGuard guard;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void nphiesContract_doesNotNeedAWaseelEligibilityCheck() {
        PatientInsurance insurance = insurance(LocalDate.now().plusDays(30));
        when(coverageContractShareService.usesNphiesApprovalCoverage(insurance)).thenReturn(true);

        assertThatCode(() -> guard.requireFreshInForce(insurance, 380L)).doesNotThrowAnyException();

        verifyNoInteractions(eligibilityRequestRepository);
    }

    @Test
    void nphiesContract_expiredInsuranceIsRefused() {
        PatientInsurance insurance = insurance(LocalDate.now().minusDays(1));
        when(coverageContractShareService.usesNphiesApprovalCoverage(insurance)).thenReturn(true);

        assertThatThrownBy(() -> guard.requireFreshInForce(insurance, 380L))
                .isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void waselContract_stillNeedsARecentEligibilityCheck() {
        PatientInsurance insurance = insurance(LocalDate.now().plusDays(30));
        when(coverageContractShareService.usesNphiesApprovalCoverage(insurance)).thenReturn(false);
        when(eligibilityRequestRepository
                .findFirstByPatientIdAndPatientInsuranceIdAndRequestStatusAndRespondedAtIsNotNullOrderByRespondedAtDesc(
                        any(), any(), any()
                ))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireFreshInForce(insurance, 380L))
                .isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("within the last 24 hours");
    }

    @Test
    void missingInsuranceIsRefusedWithoutReadingTheContract() {
        assertThatThrownBy(() -> guard.requireFreshInForce(null, 380L))
                .isInstanceOf(BadRequestAlertException.class);

        verify(coverageContractShareService, never()).usesNphiesApprovalCoverage(any());
    }

    private PatientInsurance insurance(LocalDate expirationDate) {
        PatientInsurance insurance = new PatientInsurance();
        insurance.setId(126L);
        insurance.setExpirationDate(expirationDate);
        return insurance;
    }
}
