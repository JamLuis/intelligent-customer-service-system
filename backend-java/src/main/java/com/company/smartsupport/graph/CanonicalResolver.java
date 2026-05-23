package com.company.smartsupport.graph;

import org.springframework.stereotype.Component;

import com.company.smartsupport.graph.dto.ScoreBreakdown;

@Component
public class CanonicalResolver {

    public static final double UNIQUE_KEY_WEIGHT = 0.40;
    public static final double ALIAS_WEIGHT = 0.20;
    public static final double REGEX_WEIGHT = 0.15;
    public static final double EMBEDDING_WEIGHT = 0.10;
    public static final double CODE_GRAPH_REF_WEIGHT = 0.10;
    public static final double LLM_VERIFY_WEIGHT = 0.05;
    public static final double MERGE_THRESHOLD = 0.85;

    public ScoreBreakdown score(CanonicalSignals signals) {
        double uniqueKey = hit(signals.uniqueKeyMatch()) * UNIQUE_KEY_WEIGHT;
        double alias = hit(signals.aliasMatch()) * ALIAS_WEIGHT;
        double regex = hit(signals.regexRuleMatch()) * REGEX_WEIGHT;
        double embedding = clamp(signals.embeddingSimilarity()) * EMBEDDING_WEIGHT;
        double codeGraphRef = hit(signals.codeGraphRefMatch()) * CODE_GRAPH_REF_WEIGHT;
        double llmVerify = hit(signals.llmVerified()) * LLM_VERIFY_WEIGHT;
        double total = uniqueKey + alias + regex + embedding + codeGraphRef + llmVerify;
        boolean strongDeterministicHit = uniqueKey > 0 || alias > 0 || regex > 0;
        boolean mergeAllowed = total >= MERGE_THRESHOLD && strongDeterministicHit;
        boolean embeddingOnly = embedding > 0 && uniqueKey == 0 && alias == 0 && regex == 0 && codeGraphRef == 0 && llmVerify == 0;
        boolean crossLinkHint = embeddingOnly && clamp(signals.embeddingSimilarity()) >= MERGE_THRESHOLD;
        return new ScoreBreakdown(uniqueKey, alias, regex, embedding, codeGraphRef, llmVerify, total, mergeAllowed, crossLinkHint);
    }

    private double hit(boolean value) {
        return value ? 1.0 : 0.0;
    }

    private double clamp(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }
        return Math.min(1, value);
    }

    public record CanonicalSignals(
            boolean uniqueKeyMatch,
            boolean aliasMatch,
            boolean regexRuleMatch,
            double embeddingSimilarity,
            boolean codeGraphRefMatch,
            boolean llmVerified) {
    }
}