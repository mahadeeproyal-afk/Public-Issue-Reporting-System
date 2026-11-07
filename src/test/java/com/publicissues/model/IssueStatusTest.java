package com.publicissues.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IssueStatusTest {
    @Test
    void acceptsExpectedForwardTransitions() {
        assertTrue(IssueStatus.SUBMITTED.canTransitionTo(IssueStatus.UNDER_REVIEW));
        assertTrue(IssueStatus.UNDER_REVIEW.canTransitionTo(IssueStatus.ASSIGNED));
        assertTrue(IssueStatus.ASSIGNED.canTransitionTo(IssueStatus.IN_PROGRESS));
        assertTrue(IssueStatus.IN_PROGRESS.canTransitionTo(IssueStatus.RESOLVED));
        assertTrue(IssueStatus.SUBMITTED.canTransitionTo(IssueStatus.REJECTED));
    }

    @Test
    void rejectsSkippedAndTerminalTransitions() {
        assertFalse(IssueStatus.SUBMITTED.canTransitionTo(IssueStatus.RESOLVED));
        assertFalse(IssueStatus.ASSIGNED.canTransitionTo(IssueStatus.RESOLVED));
        assertFalse(IssueStatus.RESOLVED.canTransitionTo(IssueStatus.IN_PROGRESS));
        assertFalse(IssueStatus.REJECTED.canTransitionTo(IssueStatus.UNDER_REVIEW));
        assertFalse(IssueStatus.SUBMITTED.canTransitionTo(null));
    }
}
