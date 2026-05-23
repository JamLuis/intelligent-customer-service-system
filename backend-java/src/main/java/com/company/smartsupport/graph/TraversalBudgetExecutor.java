package com.company.smartsupport.graph;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.company.smartsupport.common.SmartSupportException;
import com.company.smartsupport.graph.dto.TraversalBudget;

@Component
public class TraversalBudgetExecutor {

    private final TraversalBudget upperBound;

    public TraversalBudgetExecutor(
            @Value("${smart-support.graph.traversal-budget.max-nodes:300}") int maxNodes,
            @Value("${smart-support.graph.traversal-budget.max-edges:800}") int maxEdges,
            @Value("${smart-support.graph.traversal-budget.max-depth-hard-cap:5}") int maxDepthHardCap,
            @Value("${smart-support.graph.traversal-budget.max-fan-out-per-node:80}") int maxFanOutPerNode,
            @Value("${smart-support.graph.traversal-budget.timeout-ms:1500}") long timeoutMs) {
        this.upperBound = new TraversalBudget(maxNodes, maxEdges, maxDepthHardCap, maxFanOutPerNode, timeoutMs);
    }

    public TraversalBudget apply(TraversalBudget requested) {
        return (requested == null ? TraversalBudget.defaults() : requested).clamp(upperBound);
    }

    public <T> T execute(TraversalBudget requested, Executor executor, TraversalOperation<T> operation) {
        TraversalBudget budget = apply(requested);
        try {
            return CompletableFuture
                    .supplyAsync(() -> operation.run(budget), executor)
                    .orTimeout(Duration.ofMillis(budget.timeoutMs()).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .join();
        } catch (CompletionException ex) {
            if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new SmartSupportException("ICSS-KG-413-BUDGET_EXCEEDED", "图谱遍历超过预算限制");
            }
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw ex;
        }
    }

    @FunctionalInterface
    public interface TraversalOperation<T> {
        T run(TraversalBudget budget);
    }
}