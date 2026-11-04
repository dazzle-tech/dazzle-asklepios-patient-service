package com.dazzle.asklepios.repository;

import com.dazzle.asklepios.domain.ClaimRequest;
import com.dazzle.asklepios.integration.waseel.dto.claim.ClaimTrackingCriteria;
import org.springframework.data.jpa.domain.Specification;

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
        return specification;
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
