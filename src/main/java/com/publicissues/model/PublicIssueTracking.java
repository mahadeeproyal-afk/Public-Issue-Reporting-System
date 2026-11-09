package com.publicissues.model;

import java.time.LocalDateTime;

/** Explicit allowlist of fields available to anyone with a tracking code. */
public record PublicIssueTracking(
        String trackingCode,
        String title,
        String category,
        String status,
        LocalDateTime submittedAt) { }
