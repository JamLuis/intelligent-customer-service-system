package com.company.smartsupport.graph.dto;

public record TraversalBudget(
        int maxNodes,
        int maxEdges,
        int maxDepthHardCap,
        int maxFanOutPerNode,
        long timeoutMs) {

    public static TraversalBudget defaults() {
        return new TraversalBudget(300, 800, 5, 80, 1500);
    }

    public TraversalBudget clamp(TraversalBudget upperBound) {
        return new TraversalBudget(
                positiveMin(maxNodes, upperBound.maxNodes),
                positiveMin(maxEdges, upperBound.maxEdges),
                positiveMin(maxDepthHardCap, upperBound.maxDepthHardCap),
                positiveMin(maxFanOutPerNode, upperBound.maxFanOutPerNode),
                positiveMin(timeoutMs, upperBound.timeoutMs));
    }

    private static int positiveMin(int value, int upperBound) {
        return value <= 0 ? upperBound : Math.min(value, upperBound);
    }

    private static long positiveMin(long value, long upperBound) {
        return value <= 0 ? upperBound : Math.min(value, upperBound);
    }
}