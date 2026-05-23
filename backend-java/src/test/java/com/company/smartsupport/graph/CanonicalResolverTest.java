package com.company.smartsupport.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.company.smartsupport.graph.CanonicalResolver.CanonicalSignals;

class CanonicalResolverTest {

    private final CanonicalResolver resolver = new CanonicalResolver();

    @Test
    void uniqueKeyAndStrongSignalsAllowMerge() {
        var score = resolver.score(new CanonicalSignals(true, true, true, 0.9, true, true));

        assertThat(score.total()).isGreaterThanOrEqualTo(0.85);
        assertThat(score.mergeAllowed()).isTrue();
        assertThat(score.crossLinkHint()).isFalse();
    }

    @Test
    void embeddingOnlyNeverAutoMerges() {
        var score = resolver.score(new CanonicalSignals(false, false, false, 0.96, false, false));

        assertThat(score.mergeAllowed()).isFalse();
        assertThat(score.crossLinkHint()).isTrue();
    }

    @Test
    void deterministicSignalStillNeedsThreshold() {
        var score = resolver.score(new CanonicalSignals(true, false, false, 0.2, false, false));

        assertThat(score.total()).isLessThan(0.85);
        assertThat(score.mergeAllowed()).isFalse();
    }
}