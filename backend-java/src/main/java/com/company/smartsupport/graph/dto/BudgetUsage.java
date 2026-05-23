package com.company.smartsupport.graph.dto;

public record BudgetUsage(
        int visitedNodes,
        int visitedEdges,
        boolean truncated,
        TraversalBudget appliedBudget) {
}