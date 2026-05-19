package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInquiryResponse;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInsurancePlan;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiMappedPatientResponse;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryAddressMapper;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryPatientMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.integration.waseel.service.mapper.CchiBeneficiaryPatientDocumentMapper;import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WaseelCchiService {

    private static final Logger LOG = LogManager.getLogger(WaseelCchiService.class);

    private final RestTemplate restTemplate;
    private final WaseelTokenService tokenService;
    private final WaseelApiProperties properties;
    private final CchiBeneficiaryPatientMapper patientMapper;
    private final CchiBeneficiaryAddressMapper addressMapper;
    private final CchiBeneficiaryPatientDocumentMapper patientDocumentMapper;
    public WaseelCchiService(
            RestTemplate restTemplate,
            WaseelTokenService tokenService,
            WaseelApiProperties properties,
            CchiBeneficiaryPatientMapper patientMapper,
            CchiBeneficiaryAddressMapper addressMapper, CchiBeneficiaryPatientDocumentMapper patientDocumentMapper
    ) {
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.properties = properties;
        this.patientMapper = patientMapper;
        this.addressMapper = addressMapper;
        this.patientDocumentMapper = patientDocumentMapper;
    }

    public CchiInquiryResponse fetchBeneficiaryByDocumentId(String documentId) {
        return mockCchiInquiryResponse();

////        String token = tokenService.getToken();
//
////        String url =
////                properties.baseUrl()
////                        + "/beneficiaries/providers/"
////                        + properties.providerId()
////                        + "/patientKey/"
////                        + documentId
////                        + "/systemType/"
////                        + properties.systemType();
//
//        LOG.info("Calling Waseel URL: {}", url);
//
//        HttpHeaders headers = new HttpHeaders();
////        headers.setBearerAuth(token);
//        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
//        headers.set("User-Agent", "PostmanRuntime/7.43.0");
//
//        HttpEntity<Void> entity = new HttpEntity<>(headers);
//
//        ResponseEntity<CchiInquiryResponse> response =
//                restTemplate.exchange(
//                        url,
//                        HttpMethod.GET,
//                        entity,
//                        CchiInquiryResponse.class
//                );

//        return response.getBody();
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

        return new CchiMappedPatientResponse(
                patient,
                address,
                document
        );
    }    private CchiInquiryResponse mockCchiInquiryResponse() {
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

                java.util.List.of(
                        new CchiInsurancePlan(
                                "001093772497001",              // memberCardId
                                "48095070",                     // policyNumber
                                "2026-08-21T21:00:00.000+0000", // expiryDate
                                "true",                         // isPrimary
                                null,                           // payerId
                                "SELF",                         // relationWithSubscriber
                                "EHCPOL",                       // coverageType
                                20,                             // patientShare
                                100,                            // maxLimit
                                "12",                           // networkId
                                "a",                            // policyClassName
                                "waseel application service prov.", // policyHolder
                                "7000911508",                   // payerNphiesId
                                false                           // newPlan
                        )          )
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