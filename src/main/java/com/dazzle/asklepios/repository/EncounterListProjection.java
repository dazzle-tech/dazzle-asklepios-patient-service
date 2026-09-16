package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;

public interface EncounterListProjection {

    Long getId();

    Long getPatientId();
    String getPatientFullName();
    String getMrn();
    LocalDate getDateOfBirth();
    String getGender();

    String getDocumentType();
    String getDocumentNumber();
    String getPrimaryMobileNumber();

    String getEncounterNumber();
    LocalDate getEncounterDate();
    LocalTime getEncounterTime();

    EncounterType getEncounterType();

    Long getDepartmentId();
    String getDepartmentName();

    Long getPractitionerId();
    String getPractitionerName();

    String getDefaultServiceName();

    java.math.BigDecimal getAmount();
    String getPaymentStatus();

    String getCoverageType();
    String getPaymentType();
    String getInsuranceName();

    Boolean getTriageStarted();
    Instant getDoctorStartDateTime();

    EncounterStatus getEncounterStatus();
    TreatmentStatus getTreatmentStatus();

    Boolean getIsObserved();
}