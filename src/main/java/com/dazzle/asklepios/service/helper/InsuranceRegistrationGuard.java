package com.dazzle.asklepios.service.helper;

import com.dazzle.asklepios.client.setup.InsuranceRegistrationStatusClient;
import com.dazzle.asklepios.client.setup.dto.InsuranceRegistrationStatusDTO;
import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Service;

/**
 * Blocks registering patient insurance or visits under an insurance company, policy or class
 * that was deactivated in setup. Setup decides and returns the message telling the user what
 * is inactive and why.
 */
@Service
public class InsuranceRegistrationGuard {

    private static final String ENTITY_NAME = "insuranceRegistration";

    private final InsuranceRegistrationStatusClient insuranceRegistrationStatusClient;
    private final PayorHelper payorHelper;
    private final PatientInsuranceRepository patientInsuranceRepository;

    public InsuranceRegistrationGuard(
            InsuranceRegistrationStatusClient insuranceRegistrationStatusClient,
            PayorHelper payorHelper,
            PatientInsuranceRepository patientInsuranceRepository
    ) {
        this.insuranceRegistrationStatusClient = insuranceRegistrationStatusClient;
        this.payorHelper = payorHelper;
        this.patientInsuranceRepository = patientInsuranceRepository;
    }

    public void requireRegistrable(Long patientInsuranceId) {
        if (patientInsuranceId != null) {
            patientInsuranceRepository.findById(patientInsuranceId).ifPresent(this::requireRegistrable);
        }
    }

    public void requireRegistrable(PatientInsurance insurance) {
        if (insurance != null) {
            requireRegistrable(payerNphiesIdOf(insurance), insurance.getPolicyNumber(), insurance.getPolicyClassName());
        }
    }

    public void requireRegistrable(String payerNphiesId, String policyNumber, String className) {
        InsuranceRegistrationStatusDTO status =
                insuranceRegistrationStatusClient.check(payerNphiesId, policyNumber, className);
        if (status != null && !status.allowed()) {
            throw new BadRequestAlertException(status.message(), ENTITY_NAME, "insuranceRegistration.inactive");
        }
    }

    private String payerNphiesIdOf(PatientInsurance insurance) {
        String payerNphiesId = insurance.getPayerNphiesId();
        if (payerNphiesId != null && !payerNphiesId.isBlank()) {
            return payerNphiesId;
        }
        PayorDTO payor = payorHelper.findPayor(insurance.getPayorId(), null);
        return payor == null ? null : payor.nphiesId();
    }
}
