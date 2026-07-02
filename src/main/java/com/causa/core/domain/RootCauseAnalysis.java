package com.causa.core.domain;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

/**
 * Root Cause Analysis Domain Model
 *
 * <p>Represents the structured RCA output produced by the LLM for a given alert.
 *
 * @since 0.0.1
 */
public record RootCauseAnalysis(
    String issueTitle,
    String anomalyType,
    String rootCause,
    String issueDescription,
    String technicalDescription,
    List<PossibleSolution> possibleSolutions
) {

    public RootCauseAnalysis {
        if (issueTitle == null || issueTitle.isBlank()) {
            throw new IllegalArgumentException("issueTitle cannot be blank");
        }
        if (possibleSolutions == null) {
            possibleSolutions = Collections.emptyList();
        } else {
            possibleSolutions = Collections.unmodifiableList(new ArrayList<>(possibleSolutions));
        }
    }

    /**
     * A possible solution entry within the RCA.
     *
     * @param solution the solution description text
     * @param priority optional priority ordering (lower = higher priority)
     */
    public record PossibleSolution(
        String solution,
        Integer priority
    ) {
        public PossibleSolution {
            if (solution == null || solution.isBlank()) {
                throw new IllegalArgumentException("Solution text cannot be blank");
            }
        }

        /**
         * Creates a solution with no priority set.
         */
        public static PossibleSolution of(String solution) {
            return new PossibleSolution(solution, null);
        }
    }
}
