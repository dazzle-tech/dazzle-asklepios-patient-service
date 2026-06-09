package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.client.setup.dto.PayorDTO;
import com.dazzle.asklepios.client.setup.dto.PayorPlanDTO;
import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiCoverageClass;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiMappedPatientResponse;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryAddressMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryPatientDocumentMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryPatientInsuranceMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryPatientMapper;
import com.dazzle.asklepios.repository.PatientInsuranceRepository;
import com.dazzle.asklepios.repository.PatientRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class WaseelCchiService {

    private static final Logger LOG = LogManager.getLogger(WaseelCchiService.class);

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final CchiBeneficiaryPatientMapper patientMapper;
    private final CchiBeneficiaryAddressMapper addressMapper;
    private final CchiBeneficiaryPatientDocumentMapper patientDocumentMapper;
    private final CchiBeneficiaryPatientInsuranceMapper insuranceMapper;
    private final SetupInsuranceLookupService setupInsuranceLookupService;
    private final PatientRepository patientRepository;
    private final PatientInsuranceRepository patientInsuranceRepository;

    public WaseelCchiService(
            RestTemplate restTemplate,
            WaseelTokenService tokenService,
            WaseelApiProperties properties,
            CchiBeneficiaryPatientMapper patientMapper,
            CchiBeneficiaryAddressMapper addressMapper,
            CchiBeneficiaryPatientDocumentMapper patientDocumentMapper,
            CchiBeneficiaryPatientInsuranceMapper insuranceMapper,
            SetupInsuranceLookupService setupInsuranceLookupService,
            PatientRepository patientRepository,
            PatientInsuranceRepository patientInsuranceRepository
    ) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.properties = properties;
        this.patientMapper = patientMapper;
        this.addressMapper = addressMapper;
        this.patientDocumentMapper = patientDocumentMapper;
        this.insuranceMapper = insuranceMapper;
        this.setupInsuranceLookupService = setupInsuranceLookupService;
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
    }

    public CchiInquiryResponse fetchBeneficiaryByDocumentId(String documentId) {
        return mockCchiInquiryResponse();
    }

    public Patient fetchPatientByDocumentId(String documentId) {
        CchiMappedPatientResponse mapped = fetchMappedPatientByDocumentId(documentId);
        return mapped == null ? null : mapped.patient();
    }

    public CchiMappedPatientResponse fetchMappedPatientByDocumentId(String documentId) {
        CchiInquiryResponse response = fetchBeneficiaryByDocumentId(documentId);

        if (response == null || response.data() == null) {
            return null;
        }

        CchiBeneficiaryData beneficiary = response.data();

        Patient patient = patientMapper.toPatient(beneficiary);
        Address address = addressMapper.toAddress(beneficiary);
        PatientDocument document = patientDocumentMapper.toPatientDocument(beneficiary);

        List<PatientInsurance> insurances = mapPatientInsurances(
                patient,
                beneficiary.insurancePlans()
        );

        return new CchiMappedPatientResponse(
                patient,
                address,
                document,
                insurances
        );
    }

    private List<PatientInsurance> mapPatientInsurances(
            Patient patient,
            List<CchiInsurancePlan> insurancePlans
    ) {
        if (insurancePlans == null || insurancePlans.isEmpty()) {
            return List.of();
        }

        List<PatientInsurance> result = new ArrayList<>();

        for (CchiInsurancePlan insurancePlan : insurancePlans) {
            PayorDTO payor = resolvePayor(insurancePlan);

            PayorPlanDTO payorPlan = resolvePayorPlan(payor, insurancePlan);

            PatientInsurance insurance = insuranceMapper.toPatientInsurance(
                    insurancePlan,
                    patient,
                    payor,
                    payorPlan
            );

            result.add(insurance);
        }

        return result;
    }

    private PayorDTO resolvePayor(CchiInsurancePlan insurancePlan) {
        String payerNphiesId = clean(insurancePlan.payerNphiesId());

        if (isBlank(payerNphiesId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Payer NPHIES ID is missing from CCHI insurance plan"
            );
        }

        return setupInsuranceLookupService
                .findPayorByNphiesId(payerNphiesId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Payer returned from CCHI is not configured. NPHIES ID: " + payerNphiesId
                ));
    }

    private PayorPlanDTO resolvePayorPlan(
            PayorDTO payor,
            CchiInsurancePlan insurancePlan
    ) {
        String coverageType = clean(insurancePlan.coverageType());
        String networkId = clean(insurancePlan.networkId());
        String policyClassName = clean(insurancePlan.policyClassName());

        if (isBlank(coverageType) && isBlank(networkId) && isBlank(policyClassName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot resolve CCHI payor plan because coverageType, networkId, and policyClassName are missing"
            );
        }

        return setupInsuranceLookupService
                .findPayorPlanByCchiMatch(
                        payor.id(),
                        coverageType,
                        networkId,
                        policyClassName
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Payor plan returned from CCHI is not configured. "
                                + "payorId=" + payor.id()
                                + ", coverageType=" + coverageType
                                + ", networkId=" + networkId
                                + ", policyClassName=" + policyClassName
                ));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private CchiInquiryResponse mockCchiInquiryResponse() {
        CchiBeneficiaryData data = new CchiBeneficiaryData(
                "11111111",
                "PPN",

                "Akhil Nair",
                "Akhil",
                null,
                "Nair",
                "Nair",

                "BEN-AKHIL-001",
                "NPHIES",
                "11111111",
                null,
                null,
                null,
                null,
                null,

                "1990-01-01",
                null,
                "356",
                "RESIDENT",

                "966500000000",
                "akhil.nair@test.com",
                "966511111111",

                "Test Address",
                "Test Street",
                "Riyadh",
                "Riyadh",
                "SAU",
                "12345",

                "U",
                "MALE",
                "O+",
                "EN",
                null,
                "unknown",

                10000000097830L,
                "706",
                false,

                List.of(
                        new CchiInsurancePlan(
                                "4",
                                "12121212",
                                "357159456",
                                null,
                                "2028-09-27T21:00:00.000+0000",
                                "2021-09-27T21:00:00.000+0000",
                                "true",
                                "INS-FHIR",
                                "INS-FHIR Test Payer",
                                "INS-FHIR",
                                null,
                                "SELF",
                                "EHCPOL",
                                BigDecimal.valueOf(20),
                                BigDecimal.valueOf(1000),
                                "12",
                                null,
                                "a",
                                "Akhil Nair",
                                List.of(
                                        new CchiCoverageClass(
                                                "plan",
                                                "a",
                                                "a"
                                        )
                                ),
                                false
                        )
                )
        );

        return new CchiInquiryResponse(
                "Success",
                "200",
                "Request successful",
                false,
                data
        );
    }
}