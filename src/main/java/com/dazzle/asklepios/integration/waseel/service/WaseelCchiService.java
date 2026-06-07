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
import jakarta.transaction.Transactional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
            PatientRepository patientRepository, PatientInsuranceRepository patientInsuranceRepository) {
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
        // For local testing only.
        // Comment this line when you want to call the real Waseel API.
        return mockCchiInquiryResponse();

        /*
        String token = tokenService.getToken();

        String url =
                properties.baseUrl()
                        + "/beneficiaries/providers/"
                        + properties.providerId()
                        + "/patientKey/"
                        + documentId
                        + "/systemType/"
                        + properties.systemType();

        LOG.info("Calling Waseel URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "PostmanRuntime/7.43.0");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<CchiInquiryResponse> response =
                restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        CchiInquiryResponse.class
                );

        return response.getBody();
        */
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

        PatientDocument document =
                patientDocumentMapper.toPatientDocument(beneficiary);

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

            PayorPlanDTO payorPlan = resolvePayorPlan(
                    payor,
                    insurancePlan
            );


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
                insurancePlan.coverageType(),
                insurancePlan.networkId(),
                insurancePlan.policyClassName()
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
                "1093772497",                 // documentId
                "NI",                         // documentType

                "JAWAD JASIM AL SAEED",       // fullName
                null,                         // firstName
                null,                         // middleName
                null,                         // lastName
                null,                         // familyName

                "BEN-001",                    // beneficiaryFileId
                "NPHIES",                     // systemType
                "P1234567",                   // passportNumber
                "B1234567",                   // borderNumber
                "V1234567",                   // visaNumber
                "WORK",                       // visaType
                "Worker",                     // visitTitle
                "2027-12-31",                 // visaExpiryDate

                "1995-04-15",                 // dob
                "EH-123456",                  // eHealthId
                "113",                        // nationality
                "RESIDENT",                   // residencyType

                "966500000000",               // contactNumber
                "test@example.com",           // email
                "966511111111",               // emergencyNumber

                "Building 12, Floor 3",       // addressLine
                "King Fahad Road",            // streetLine
                "Riyadh",                     // city
                "Riyadh Region",              // state
                "SAU",                        // country
                "12345",                      // postalCode

                "M",                          // martialStatus
                "MALE",                       // gender
                "O+",                         // bloodGroup
                "AR",                         // preferredLanguage
                "MUSLIM",                     // religion
                "DOCTOR",                     // occupation

                10000000097830L,              // nphiesId
                "501",                        // providerId
                false,                        // isNewBorn

                List.of(
                        new CchiInsurancePlan(
                                null,                               // planId
                                "001093772497001",                  // memberCardId
                                "48095070",                         // policyNumber
                                null,                               // groupNumber
                                "2026-08-21T21:00:00.000+0000",     // expiryDate
                                "2025-08-22T21:00:00.000+0000",     // issueDate
                                "true",                             // isPrimary
                                null,                               // payerId
                                null,                               // payerName
                                "7000911508",                       // payerNphiesId
                                null,                               // tpaNphiesId
                                "SELF",                             // relationWithSubscriber
                                "EHCPOL",                           // coverageType
                                BigDecimal.valueOf(20),             // patientShare
                                BigDecimal.valueOf(100),            // maxLimit
                                "12",                               // networkId
                                "7001454136",                       // sponsorNumber
                                "a",                                // policyClassName
                                "waseel application service prov.", // policyHolder
                                List.of(
                                        new CchiCoverageClass(
                                                "plan",
                                                "a",
                                                ""
                                        )
                                ),                         // coverageClassList
                                false                               // newPlan
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