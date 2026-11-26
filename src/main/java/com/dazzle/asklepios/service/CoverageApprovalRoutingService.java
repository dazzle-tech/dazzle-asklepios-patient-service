package com.dazzle.asklepios.service;

import com.dazzle.asklepios.client.setup.dto.CoverageContractResolveDtos;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.integration.waseel.service.EncounterInsuranceEligibilityService;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads "Approval Coverage Co." from the matched coverage contract.
 *
 * NPHIES contracts use the local approval flow: the contract pre-approval decides,
 * a local request is opened, and nothing is sent to Waseel (no eligibility check,
 * no Waseel submission, no price-list / payor-plan pre-auth flags).
 * Any other value keeps the existing Waseel flow unchanged.
 */
@Service
public class CoverageApprovalRoutingService {

    static final String LOCAL_APPROVAL_COMPANY = "NPHIES";

    private static final Logger LOG = LoggerFactory.getLogger(CoverageApprovalRoutingService.class);

    private final CoverageContractShareService coverageContractShareService;
    private final EncounterInsuranceEligibilityService encounterInsuranceEligibilityService;
    private final PatientInsuranceRepository patientInsuranceRepository;

    public CoverageApprovalRoutingService(
            CoverageContractShareService coverageContractShareService,
            EncounterInsuranceEligibilityService encounterInsuranceEligibilityService,
            PatientInsuranceRepository patientInsuranceRepository
    ) {
        this.coverageContractShareService = coverageContractShareService;
        this.encounterInsuranceEligibilityService = encounterInsuranceEligibilityService;
        this.patientInsuranceRepository = patientInsuranceRepository;
    }

    public static boolean usesLocalApproval(CoverageContractResolveDtos.Response resolved) {
        if (resolved == null || !resolved.matched() || resolved.contract() == null) {
            return false;
        }
        String company = resolved.contract().approvalCoverageCompany();
        return company != null && LOCAL_APPROVAL_COMPANY.equalsIgnoreCase(company.trim());
    }

    public boolean usesLocalApproval(PatientInsurance insurance, PatientServiceAndProduct item) {
        if (insurance == null) {
            return false;
        }
        try {
            return usesLocalApproval(coverageContractShareService.resolveVisitContract(insurance, item));
        } catch (RuntimeException exception) {
            LOG.warn(
                    "[APPROVAL_ROUTING] Contract could not be read for patientInsuranceId={}",
                    insurance.getId(),
                    exception
            );
            return false;
        }
    }

    public boolean encounterUsesLocalApproval(Long encounterId) {
        if (encounterId == null) {
            return false;
        }
        Long insuranceId = encounterInsuranceEligibilityService.resolveEncounterPatientInsuranceId(encounterId);
        if (insuranceId == null) {
            return false;
        }
        PatientInsurance insurance = patientInsuranceRepository.findById(insuranceId).orElse(null);
        boolean local = usesLocalApproval(
                insurance,
                PatientServiceAndProduct.builder().encounterId(encounterId).build()
        );
        if (local) {
            LOG.info("[APPROVAL_ROUTING] encounterId={} uses the local NPHIES approval flow (no Waseel)", encounterId);
        }
        return local;
    }
}
