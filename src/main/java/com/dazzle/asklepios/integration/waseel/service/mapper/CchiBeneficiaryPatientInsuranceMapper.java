package com.dazzle.asklepios.integration.waseel.service.mapper;

import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiCoverageClass;
import com.dazzle.asklepios.integration.waseel.dto.cchi.CchiInsurancePlan;
import com.dazzle.asklepios.web.rest.errors.BadRequestAlertException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.hibernate.id.IdentifierGenerator.ENTITY_NAME;

@Component
public class CchiBeneficiaryPatientInsuranceMapper {

    public PatientInsurance toPatientInsurance(
            CchiInsurancePlan source,
            Patient patient
    ) {
        if (source == null) {
            return null;
        }

        PatientInsurance insurance = new PatientInsurance();

        mapToExistingPatientInsurance(
                insurance,
                source,
                patient
        );

        return insurance;
    }

    public void mapToExistingPatientInsurance(
            PatientInsurance insurance,
            CchiInsurancePlan source,
            Patient patient
    ) {
        if (insurance == null || source == null) {
            return;
        }

        if (patient == null) {
            throw new BadRequestAlertException(
                    "Patient is required before mapping patient insurance",
                    ENTITY_NAME,
                    "patient.required"
            );
        }

        insurance.setPatient(patient);

        insurance.setPayorId(null);
        insurance.setPlanId(null);

        insurance.setMemberCardId(clean(source.memberCardId()));
        insurance.setPolicyNumber(requiredString(source.policyNumber(), "policyNumber"));
        insurance.setGroupNumber(clean(source.groupNumber()));

        insurance.setPayerNphiesId(clean(source.payerNphiesId()));

        insurance.setNetworkId(clean(source.networkId()));
        insurance.setSponsorNumber(clean(source.sponsorNumber()));

        insurance.setCoverageType(clean(source.coverageType()));
        insurance.setRelationWithSubscriber(clean(source.relationWithSubscriber()));

        insurance.setPolicyClassName(getPolicyClassName(source));
        insurance.setPolicyHolderName(clean(source.policyHolder()));

        insurance.setIssueDate(parseDate(source.issueDate()));

        LocalDate expiryDate = parseDate(source.expiryDate());

        if (expiryDate == null) {
            throw new BadRequestAlertException(
                    "Missing or invalid expiryDate from Waseel insurance plan",
                    ENTITY_NAME,
                    "expiryDate.invalid"
            );
        }

        insurance.setExpirationDate(expiryDate);

        insurance.setPatientShare(toBigDecimal(source.patientShare()));
        insurance.setMaxLimit(toBigDecimal(source.maxLimit()));

        insurance.setWaseelNewPlan(toBoolean(source.newPlan()));
        insurance.setIsPrimary(toBoolean(source.isPrimary()));
    }

    private String getPolicyClassName(CchiInsurancePlan source) {
        String directPolicyClassName = clean(source.policyClassName());

        if (!isBlank(directPolicyClassName)) {
            return directPolicyClassName;
        }

        if (source.coverageClassList() == null || source.coverageClassList().isEmpty()) {
            return null;
        }

        CchiCoverageClass firstClass = source.coverageClassList().get(0);

        return firstNonBlank(
                clean(firstClass.name()),
                clean(firstClass.value())
        );
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }

        String date = value.toString().trim();

        if (date.isEmpty()) {
            return null;
        }

        try {
            if (date.length() >= 10 && date.charAt(4) == '-' && date.charAt(7) == '-') {
                return LocalDate.parse(date.substring(0, 10));
            }
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(date);
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception ignored) {
        }

        try {
            return LocalDate.parse(date, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        } catch (Exception ignored) {
        }

        return null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }

        String text = value.toString().trim();

        if (text.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        String text = value.toString().trim();

        return text.equalsIgnoreCase("true")
                || text.equalsIgnoreCase("yes")
                || text.equals("1");
    }

    private String requiredString(Object value, String fieldName) {
        String text = clean(value);

        if (isBlank(text)) {
            throw new BadRequestAlertException(
                    "Missing required Waseel field: " + fieldName,
                    ENTITY_NAME,
                    fieldName + ".required"
            );
        }

        return text;
    }

    private String clean(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();

        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }
}