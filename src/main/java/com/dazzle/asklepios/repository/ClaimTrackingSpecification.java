package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.domain.Patient;
import com.dazzle.asklepios.domain.PatientEncounter;
import com.dazzle.asklepios.domain.PatientInsurance;
import com.dazzle.asklepios.domain.enumeration.waseelIntegration.ClaimStatus;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ClaimTrackingSpecification {

    private ClaimTrackingSpecification() {
    }

    public static Specification<ClaimRequest> of(ClaimTrackingCriteria criteria) {
        Specification<ClaimRequest> specification = Specification.where(null);
        if (criteria == null) {
            return specification;
        }

        if (criteria.status() != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), criteria.status()));
        }
        if (criteria.claimType() != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("claimType"), criteria.claimType()));
        }
        if (criteria.claimSubType() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("claimSubType"), criteria.claimSubType()));
        }

        specification = andContains(specification, "claimReference", criteria.claimReference());
        specification = andContains(specification, "provClaimNo", criteria.provClaimNo());
        specification = andContains(specification, "uploadName", criteria.uploadName());
        specification = andContains(specification, "preAuthRefNo", criteria.preAuthRefNo());
        specification = andIdEquals(specification, "encounterId", criteria.encounterId());
        specification = andIdEquals(specification, "patientId", criteria.patientId());
        specification = andEncounterNumberContains(specification, criteria.encounterNumber());
        specification = andMedicalRecordNumberContains(specification, criteria.medicalRecordNumber());
        specification = andSubmittedBetween(specification, criteria.submittedFrom(), criteria.submittedTo());
        specification = andPayor(specification, criteria.payorId(), criteria.payerNphiesId());
        if (criteria.waseelStatus() != null) {
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("waseelStatus"), criteria.waseelStatus()));
        }
        if (criteria.latestPerEncounter()) {
            specification = specification.and(latestPerEncounter());
        }
        return specification;
    }

    /**
     * Claims whose result can still change on Waseel: submitted and not decided yet,
     * or accepted while the approved amount has not come back.
     */
    public static Specification<ClaimRequest> awaitingWaseelResult() {
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("provClaimNo")),
                cb.or(
                        cb.equal(root.get("status"), ClaimStatus.SUBMITTED),
                        cb.and(
                                cb.equal(root.get("status"), ClaimStatus.ACCEPTED),
                                cb.isNull(root.get("approvedAmount"))
                        )
                )
        );
    }

    /**
     * Keeps only the newest claim of each encounter. Older attempts stay in the encounter's claim log.
     * Claims without an encounter are kept as they are.
     */
    private static Specification<ClaimRequest> latestPerEncounter() {
        return (root, query, cb) -> {
            Subquery<Long> newerClaims = query.subquery(Long.class);
            Root<ClaimRequest> newer = newerClaims.from(ClaimRequest.class);
            newerClaims.select(newer.get("id"))
                    .where(
                            cb.equal(newer.get("encounterId"), root.get("encounterId")),
                            cb.greaterThan(newer.get("id"), root.get("id"))
                    );
            return cb.or(cb.isNull(root.get("encounterId")), cb.not(cb.exists(newerClaims)));
        };
    }

    /**
     * The payor is read from the encounter's insurance, the same source statements use.
     * A claim matches the local payor id or the NPHIES payer id.
     */
    private static Specification<ClaimRequest> andPayor(
            Specification<ClaimRequest> specification,
            Long payorId,
            String payerNphiesId
    ) {
        String nphiesId = normalize(payerNphiesId);
        if (payorId == null && nphiesId == null) {
            return specification;
        }
        return specification.and((root, query, cb) -> {
            Subquery<Long> encounterIds = query.subquery(Long.class);
            Root<PatientEncounter> encounter = encounterIds.from(PatientEncounter.class);
            encounterIds.select(encounter.get("id"))
                    .where(encounter.get("patientInsuranceId").in(insuranceIds(query, cb, payorId, nphiesId)));
            return root.get("encounterId").in(encounterIds);
        });
    }

    private static Subquery<Long> insuranceIds(
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Long payorId,
            String nphiesId
    ) {
        Subquery<Long> insuranceIds = query.subquery(Long.class);
        Root<PatientInsurance> insurance = insuranceIds.from(PatientInsurance.class);
        List<Predicate> matches = new ArrayList<>();
        if (payorId != null) {
            matches.add(cb.equal(insurance.get("payorId"), payorId));
        }
        if (nphiesId != null) {
            matches.add(cb.equal(cb.lower(cb.trim(insurance.get("payerNphiesId"))), nphiesId));
        }
        return insuranceIds.select(insurance.get("id")).where(cb.or(matches.toArray(Predicate[]::new)));
    }

    private static Specification<ClaimRequest> andEncounterNumberContains(
            Specification<ClaimRequest> specification,
            String raw
    ) {
        String value = normalize(raw);
        if (value == null) {
            return specification;
        }
        String pattern = "%" + value + "%";
        return specification.and((root, query, cb) -> {
            Subquery<Long> encounterIds = query.subquery(Long.class);
            Root<PatientEncounter> encounter = encounterIds.from(PatientEncounter.class);
            encounterIds.select(encounter.get("id"))
                    .where(cb.like(cb.lower(encounter.get("encounterNumber")), pattern));
            return root.get("encounterId").in(encounterIds);
        });
    }

    private static Specification<ClaimRequest> andMedicalRecordNumberContains(
            Specification<ClaimRequest> specification,
            String raw
    ) {
        String value = normalize(raw);
        if (value == null) {
            return specification;
        }
        String pattern = "%" + value + "%";
        return specification.and((root, query, cb) -> {
            Subquery<Long> patientIds = query.subquery(Long.class);
            Root<Patient> patient = patientIds.from(Patient.class);
            patientIds.select(patient.get("id"))
                    .where(cb.like(cb.lower(patient.get("medicalRecordNumber")), pattern));
            return root.get("patientId").in(patientIds);
        });
    }

    private static Specification<ClaimRequest> andSubmittedBetween(
            Specification<ClaimRequest> specification,
            Instant from,
            Instant to
    ) {
        Specification<ClaimRequest> result = specification;
        if (from != null) {
            result = result.and((root, query, cb) ->
                    cb.greaterThanOrEqualTo(root.get("submittedAt"), from));
        }
        if (to != null) {
            result = result.and((root, query, cb) -> cb.lessThan(root.get("submittedAt"), to));
        }
        return result;
    }

    private static Specification<ClaimRequest> andContains(
            Specification<ClaimRequest> specification,
            String field,
            String raw
    ) {
        String value = normalize(raw);
        if (value == null) {
            return specification;
        }
        String pattern = "%" + value + "%";
        return specification.and((root, query, cb) -> cb.like(cb.lower(root.get(field)), pattern));
    }

    private static Specification<ClaimRequest> andIdEquals(
            Specification<ClaimRequest> specification,
            String field,
            String raw
    ) {
        String value = normalize(raw);
        if (value == null) {
            return specification;
        }
        if (!value.matches("\\d+")) {
            return specification.and((root, query, cb) -> cb.disjunction());
        }
        Long id = Long.valueOf(value);
        return specification.and((root, query, cb) -> cb.equal(root.get(field), id));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase();
        return value.isEmpty() ? null : value;
    }
}
