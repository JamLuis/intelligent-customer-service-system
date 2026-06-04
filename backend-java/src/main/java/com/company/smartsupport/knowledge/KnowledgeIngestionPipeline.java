package com.company.smartsupport.knowledge;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestionPipeline {

    private final KnowledgeIngestionOrchestrator orchestrator;

    public KnowledgeIngestionPipeline(KnowledgeIngestionOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void run(String projectId, String sourceId) {
        orchestrator.run(projectId, sourceId);
    }
}
