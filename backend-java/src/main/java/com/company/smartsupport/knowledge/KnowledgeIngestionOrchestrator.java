package com.company.smartsupport.knowledge;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestionOrchestrator {

    private final KnowledgeRepository knowledgeRepository;
    private final KnowledgeTaskStateService taskStateService;
    private final ParseStageService parseStageService;
    private final EmbeddingStageService embeddingStageService;
    private final ExtractStageService extractStageService;
    private final GraphBuildStageService graphBuildStageService;

    public KnowledgeIngestionOrchestrator(
            KnowledgeRepository knowledgeRepository,
            KnowledgeTaskStateService taskStateService,
            ParseStageService parseStageService,
            EmbeddingStageService embeddingStageService,
            ExtractStageService extractStageService,
            GraphBuildStageService graphBuildStageService) {
        this.knowledgeRepository = knowledgeRepository;
        this.taskStateService = taskStateService;
        this.parseStageService = parseStageService;
        this.embeddingStageService = embeddingStageService;
        this.extractStageService = extractStageService;
        this.graphBuildStageService = graphBuildStageService;
    }

    public void run(String projectId, String sourceId) {
        KnowledgeRepository.SourceRecord source = knowledgeRepository.findSource(projectId, sourceId);
        try {
            taskStateService.reset(projectId, sourceId);
            taskStateService.update(projectId, sourceId, "parsing", "running", "pending", "pending", null);

            ParseStageService.ParseStageResult parseResult = parseStageService.run(projectId, source);
            embeddingStageService.embedBlocks(projectId, parseResult.blocks());
            taskStateService.completeLatestTask(sourceId, "parse", Map.of("blocks", parseResult.blocks().size()));

            taskStateService.createTask(sourceId, "extract");
            taskStateService.update(projectId, sourceId, "extracted", "success", "running", "pending", null);
            ExtractStageService.ExtractStageResult extractResult = extractStageService.run(projectId, source, parseResult.blocks(), parseResult.persistedBlockIds());
            taskStateService.completeLatestTask(sourceId, "extract", Map.of("entities", extractResult.graphNodes().size(), "relations", extractResult.graphEdges().size()));

            taskStateService.createTask(sourceId, "graph_build");
            taskStateService.update(projectId, sourceId, "graph_ready", "success", "success", "running", null);
            KnowledgeRepository.GraphBuildRecord graph = graphBuildStageService.run(projectId, source, extractResult.graphNodes(), extractResult.graphEdges());
            taskStateService.completeLatestTask(sourceId, "graph_build", Map.of(
                    "graphId", graph.graphId(),
                    "revisionId", graph.revisionId(),
                    "nodes", extractResult.graphNodes().size(),
                    "edges", extractResult.graphEdges().size()));
            taskStateService.update(projectId, sourceId, "graph_ready", "success", "success", "success", null);
        } catch (RuntimeException ex) {
            taskStateService.failAllKnownStages(projectId, sourceId, ex);
            throw ex;
        }
    }
}
