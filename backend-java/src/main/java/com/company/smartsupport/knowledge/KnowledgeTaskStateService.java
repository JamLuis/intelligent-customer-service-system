package com.company.smartsupport.knowledge;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class KnowledgeTaskStateService {

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeTaskStateService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    public void reset(String projectId, String sourceId) {
        knowledgeRepository.resetGeneratedArtifacts(projectId, sourceId);
    }

    public void update(String projectId, String sourceId, String stage, String parseStatus, String extractStatus, String graphStatus, String errorMessage) {
        knowledgeRepository.updateSourceStage(projectId, sourceId, stage, parseStatus, extractStatus, graphStatus, errorMessage);
    }

    public void createTask(String sourceId, String taskType) {
        knowledgeRepository.createTask(sourceId, taskType);
    }

    public void completeLatestTask(String sourceId, String taskType, Map<String, Object> result) {
        knowledgeRepository.completeLatestTask(sourceId, taskType, result);
    }

    public void failAllKnownStages(String projectId, String sourceId, RuntimeException ex) {
        knowledgeRepository.updateSourceStage(projectId, sourceId, "failed", "failed", "failed", "failed", ex.getMessage());
        knowledgeRepository.failLatestTask(sourceId, "parse", ex.getMessage());
        knowledgeRepository.failLatestTask(sourceId, "extract", ex.getMessage());
        knowledgeRepository.failLatestTask(sourceId, "graph_build", ex.getMessage());
    }
}
