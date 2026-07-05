package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.domain.Address;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientDocument;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.config.WaseelApiProperties;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiBeneficiaryData;
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
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
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
        if (documentId == null || documentId.trim().isEmpty()) {
            throw new BadRequestAlertException(
                    "Document ID is required",
                    "waseelCchi",
                    "documentId.required"
            );
        }

        try {
            // TODO real API call later
            return mockCchiInquiryResponse();

        } catch (HttpClientErrorException.BadRequest ex) {
            LOG.warn("[CCHI] Bad request documentId={} response={}", documentId, ex.getResponseBodyAsString());

            throw new BadRequestAlertException(
                    extractWaseelErrorMessage(ex.getResponseBodyAsString(), "Invalid CCHI request"),
                    "waseelCchi",
                    "waseel.cchi.badRequest"
            );

        } catch (HttpClientErrorException ex) {
            LOG.warn("[CCHI] Client error documentId={} status={} response={}",
                    documentId,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString()
            );

            throw new BadRequestAlertException(
                    extractWaseelErrorMessage(ex.getResponseBodyAsString(), "CCHI request failed"),
                    "waseelCchi",
                    "waseel.cchi.clientError"
            );

        } catch (ResourceAccessException ex) {
            LOG.error("[CCHI] Waseel connection failed documentId={}", documentId, ex);

            throw new BadRequestAlertException(
                    "Unable to connect to Waseel CCHI service",
                    "waseelCchi",
                    "waseel.cchi.connectionFailed"
            );

        } catch (Exception ex) {
            LOG.error("[CCHI] Unexpected error documentId={}", documentId, ex);

            throw new BadRequestAlertException(
                    "Failed to fetch patient from CCHI",
                    "waseelCchi",
                    "waseel.cchi.failed"
            );
        }
    }

    private String extractWaseelErrorMessage(String responseBody, String fallback) {
        if (responseBody == null || responseBody.isBlank()) {
            return fallback;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(responseBody);

            if (node.hasNonNull("message")) return node.get("message").asText();
            if (node.hasNonNull("error")) return node.get("error").asText();
            if (node.hasNonNull("detail")) return node.get("detail").asText();
            if (node.hasNonNull("description")) return node.get("description").asText();

        } catch (Exception ignored) {
            // ignore parsing error
        }

        return fallback;
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
                "2456789123",          // documentId
                "PRC",               // documentType

                "Mohammed Ahmed Saleh Alharbi", // fullName
                "Mohammed",            // firstName
                "Ahmed",               // middleName
                "Saleh",               // lastName
                "Alharbi",             // familyName

                "240600003",           // beneficiaryFileId
                "",                    // systemType
                "",                    // passportNumber
                "",                    // borderNumber
                "",                    // visaNumber
                "",                    // visaType
                "",                    // visitTitle
                "2034-01-01",          // visaExpiryDate

                "1995-05-18",          // dob
                "",                    // eHealthId
                "Saudi",               // nationality
                "Resident",            // residencyType

                "966501234567",        // contactNumber
                "mohammed@test.com",   // email
                "966500000000",        // emergencyNumber

                "Building 25, Al Olaya", // addressLine
                "King Fahad Road",       // streetLine
                "Riyadh",                // city
                "Riyadh",                // state
                "Saudi Arabia",          // country
                "12211",                 // postalCode

                "Married",             // martialStatus
                "male",                // gender
                "O+",                  // bloodGroup
                "Arabic",              // preferredLanguage
                "Islam",               // religion
                "Business",            // occupation

                0L,                    // nphiesId
                "",                    // providerId
                false,                 // isNewBorn

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