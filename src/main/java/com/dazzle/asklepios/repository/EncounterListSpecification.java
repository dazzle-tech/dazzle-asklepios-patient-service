package com.dazzle.asklepios.repository;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.PatientServiceAndProduct;
import com.dazzle.asklepios.domain.enumeration.EncounterReason;
import com.dazzle.asklepios.domain.enumeration.EncounterStatus;
import com.dazzle.asklepios.domain.enumeration.EncounterType;
import com.dazzle.asklepios.domain.enumeration.PaymentStatus;
import com.dazzle.asklepios.domain.enumeration.TreatmentStatus;
import com.dazzle.asklepios.domain.enumeration.billing.BillingCoverageType;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class EncounterListSpecification {

    private EncounterListSpecification() {
    }

    public static Specification<PatientEncounter> facilityId(Long facilityId) {
        return (root, query, cb) ->
                cb.equal(root.get("facilityId"), facilityId);
    }

    public static Specification<PatientEncounter> encounterDateBetween(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return (root, query, cb) ->
                cb.between(
                        root.get("encounterDate"),
                        fromDate,
                        toDate
                );
    }

    public static Specification<PatientEncounter> patientNameContains(
            String patientName
    ) {
        return (root, query, cb) -> {

            Join<PatientEncounter, Patient> patient =
                    root.join("patient", JoinType.INNER);

            String value = "%" + patientName.trim().toLowerCase() + "%";

            return cb.or(
                    cb.like(
                            cb.lower(patient.get("firstName")),
                            value
                    ),
                    cb.like(
                            cb.lower(patient.get("secondName")),
                            value
                    ),
                    cb.like(
                            cb.lower(patient.get("thirdName")),
                            value
                    ),
                    cb.like(
                            cb.lower(patient.get("lastName")),
                            value
                    )
            );
        };
    }

    public static Specification<PatientEncounter> mrnContains(
            String mrn
    ) {
        return (root, query, cb) -> {

            Join<PatientEncounter, Patient> patient =
                    root.join("patient", JoinType.INNER);

            return cb.like(
                    cb.lower(patient.get("medicalRecordNumber")),
                    "%" + mrn.trim().toLowerCase() + "%"
            );
        };
    }

    public static Specification<PatientEncounter> departmentIdEquals(
            Long departmentId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("departmentId"), departmentId);
    }

    public static Specification<PatientEncounter> practitionerIdEquals(
            Long practitionerId
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("practitionerId"), practitionerId);
    }

    public static Specification<PatientEncounter> coverageTypeEquals(
            BillingCoverageType coverageType
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("coverageType"), coverageType);
    }

    public static Specification<PatientEncounter> encounterTypeEquals(
            EncounterType encounterType
    ) {
        return (root, query, cb) ->
                cb.equal(root.get("encounterType"), encounterType);
    }

    public static Specification<PatientEncounter> encounterNumberContains(
            String encounterNumber
    ) {
        return (root, query, cb) ->
                cb.like(
                        cb.lower(root.get("encounterNumber")),
                        "%" + encounterNumber.trim().toLowerCase() + "%"
                );
    }

    public static Specification<PatientEncounter> encounterStatusIn(
            List<EncounterStatus> statuses
    ) {
        return (root, query, cb) ->
                root.get("encounterStatus").in(statuses);
    }

    public static Specification<PatientEncounter> treatmentStatusIn(
            List<TreatmentStatus> statuses
    ) {
        return (root, query, cb) ->
                root.get("status").in(statuses);
    }

    public static Specification<PatientEncounter> encounterReasonIn(
            List<EncounterReason> reasons
    ) {
        return (root, query, cb) ->
                root.get("encounterReason").in(reasons);
    }

    public static Specification<PatientEncounter> startedDateFrom(
            Instant startedFrom
    ) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(
                        root.get("startedDate"),
                        startedFrom
                );
    }

    public static Specification<PatientEncounter> startedDateTo(
            Instant startedTo
    ) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(
                        root.get("startedDate"),
                        startedTo
                );
    }

    public static Specification<PatientEncounter> hasPaymentStatus(
            PaymentStatus paymentStatus
    ) {
        return (root, query, cb) -> {

            var subquery = query.subquery(Long.class);

            var service =
                    subquery.from(PatientServiceAndProduct.class);

            subquery.select(service.get("id"));

            subquery.where(
                    cb.equal(
                            service.get("encounterId"),
                            root.get("id")
                    ),
                    cb.equal(
                            service.get("paymentStatus"),
                            paymentStatus
                    )
            );

            return cb.exists(subquery);
        };
    }

    public static Specification<PatientEncounter> insuranceNameContains(
            String insuranceName
    ) {
        return (root, query, cb) -> {

            Join<PatientEncounter, Patient> patient =
                    root.join("patient", JoinType.INNER);

            Join<Patient, PatientInsurance> insurance =
                    patient.join("insurances", JoinType.INNER);

            return cb.like(
                    cb.lower(insurance.get("payerName")),
                    "%" + insuranceName.trim().toLowerCase() + "%"
            );
        };
    }
}