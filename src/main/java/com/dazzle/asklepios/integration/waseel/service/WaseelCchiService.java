package com.dazzle.asklepios.integration.waseel.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
        this.patientRepository = patientRepository;
        this.patientInsuranceRepository = patientInsuranceRepository;
    }

    public CchiInquiryResponse fetchBeneficiaryByDocumentId(String documentId) {
        // TODO: Replace mock response with real Waseel CCHI API call when integration is ready.
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
            PatientInsurance insurance = insuranceMapper.toPatientInsurance(
                    insurancePlan,
                    patient
            );

            result.add(insurance);
        }

        return result;
    }

    // =========================================================
    // TEST MOCK DATA
    // Temporary mock response used during development/testing.
    // Keep until real Waseel CCHI API integration is completed.
    // =========================================================

    private CchiInquiryResponse mockCchiInquiryResponse() {
        CchiBeneficiaryData data = new CchiBeneficiaryData(
                "1254562145",
                "National Card",

                "Thmd Adel Ramy Sami",
                "",
                "",
                "",
                "",

                "240600003",
                "",
                "",
                "",
                "",
                "",
                "",
                "",

                "2020-02-05",
                "",
                "",
                "",

                "",
                "",
                "",

                "",
                "",
                "",
                "",
                "",
                "",

                "Unmarried",
                "male",
                "",
                "",
                "",
                "Business",

                0L,
                "",
                false,

                List.of(
                        new CchiInsurancePlan(

                                null,

                                "74915036",

                                "17452394",

                                null,

                                "2028-03-16",

                                null,

                                "false",

                                null,

                                "Insurance Company Testing Payer",

                                "INS-FHIR",

                                null,

                                "self",

                                "EHCPOL",

                                BigDecimal.ZERO,

                                BigDecimal.ZERO,

                                null,

                                null,

                                null,

                                "ELAL CONSTRUCTION",

                                List.of(),

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
    }}