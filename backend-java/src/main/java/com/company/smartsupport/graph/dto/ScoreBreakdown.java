package com.company.smartsupport.graph.dto;

public record ScoreBreakdown(
        double uniqueKey,
        double alias,
        double regex,
        double embedding,
        double codeGraphRef,
        double llmVerify,
        double total,
        boolean mergeAllowed,
        boolean crossLinkHint) {
}