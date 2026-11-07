package com.publicissues.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Complaint statuses and the transitions allowed by the issue workflow. */
public enum IssueStatus {
    SUBMITTED,
    UNDER_REVIEW,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    REJECTED;

    private static final Map<IssueStatus, Set<IssueStatus>> NEXT = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW, REJECTED),
            UNDER_REVIEW, EnumSet.of(ASSIGNED, REJECTED),
            ASSIGNED, EnumSet.of(IN_PROGRESS, REJECTED),
            IN_PROGRESS, EnumSet.of(RESOLVED, REJECTED),
            RESOLVED, EnumSet.noneOf(IssueStatus.class),
            REJECTED, EnumSet.noneOf(IssueStatus.class));

    public boolean canTransitionTo(IssueStatus next) {
        return next != null && NEXT.get(this).contains(next);
    }
}
