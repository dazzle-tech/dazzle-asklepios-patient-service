package com.dazzle.asklepios.integration.waseel.service;

import com.dazzle.asklepios.integration.waseel.dto.eligibility.EligibilityCoverageDTO;

import java.util.List;

/**
 * Selects the eligibility coverage row that belongs to a stored patient insurance plan.
 */
final class EligibilityCoverageMatcher {

    record MatchResult(
            EligibilityCoverageDTO coverage,
            boolean policyMatched
    ) {
        static MatchResult of(
                EligibilityCoverageDTO coverage,
                boolean policyMatched
        ) {
            return new MatchResult(coverage, policyMatched);
        }
    }

    private EligibilityCoverageMatcher() {
    }

    static EligibilityCoverageDTO resolve(
            List<EligibilityCoverageDTO> coverages,
            String memberCardId,
            String policyNumber
    ) {
        MatchResult match = resolveWithPolicyMatch(coverages, memberCardId, policyNumber);
        return match == null ? null : match.coverage();
    }

    static MatchResult resolveWithPolicyMatch(
            List<EligibilityCoverageDTO> coverages,
            String memberCardId,
            String policyNumber
    ) {
        if (coverages == null || coverages.isEmpty()) {
            return null;
        }

        String member = clean(memberCardId);
        String policy = clean(policyNumber);

        if (member != null && policy != null) {
            for (EligibilityCoverageDTO coverage : coverages) {
                if (member.equals(clean(coverage.memberId()))
                        && policy.equals(clean(coverage.policyNumber()))) {
                    return MatchResult.of(coverage, true);
                }
            }
        }

        if (member != null) {
            List<EligibilityCoverageDTO> memberMatches =
                    coverages.stream()
                            .filter(
                                    coverage ->
                                            member.equals(
                                                    clean(
                                                            coverage.memberId()
                                                    )
                                            )
                            )
                            .toList();

            if (memberMatches.size() == 1) {
                EligibilityCoverageDTO coverage = memberMatches.get(0);
                boolean policyMatched =
                        policy != null
                                && policy.equals(
                                        clean(
                                                coverage.policyNumber()
                                        )
                                );

                return MatchResult.of(coverage, policyMatched);
            }
        }

        if (policy != null) {
            List<EligibilityCoverageDTO> policyMatches =
                    coverages.stream()
                            .filter(
                                    coverage ->
                                            policy.equals(
                                                    clean(
                                                            coverage.policyNumber()
                                                    )
                                            )
                            )
                            .toList();

            if (policyMatches.size() == 1) {
                return MatchResult.of(policyMatches.get(0), true);
            }
        }

        if (coverages.size() == 1) {
            EligibilityCoverageDTO coverage = coverages.get(0);
            boolean policyMatched =
                    policy != null
                            && policy.equals(
                                    clean(
                                            coverage.policyNumber()
                                    )
                            );

            return MatchResult.of(coverage, policyMatched);
        }

        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }

        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
