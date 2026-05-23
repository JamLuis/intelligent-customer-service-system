package com.company.smartsupport.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.company.smartsupport.graph.dto.TraversalBudget;

class TraversalBudgetTest {

    @Test
    void clampsCallerBudgetToUpperBound() {
        TraversalBudget requested = new TraversalBudget(1000, 1200, 9, 200, 5000);
        TraversalBudget upperBound = new TraversalBudget(300, 800, 5, 80, 1500);

        TraversalBudget applied = requested.clamp(upperBound);

        assertThat(applied.maxNodes()).isEqualTo(300);
        assertThat(applied.maxEdges()).isEqualTo(800);
        assertThat(applied.maxDepthHardCap()).isEqualTo(5);
        assertThat(applied.maxFanOutPerNode()).isEqualTo(80);
        assertThat(applied.timeoutMs()).isEqualTo(1500);
    }

    @Test
    void nonPositiveCallerValuesUseDefaults() {
        TraversalBudget requested = new TraversalBudget(0, -1, 0, 0, 0);
        TraversalBudget upperBound = new TraversalBudget(300, 800, 5, 80, 1500);

        TraversalBudget applied = requested.clamp(upperBound);

        assertThat(applied).isEqualTo(upperBound);
    }
}