package com.company.smartsupport.knowledge;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.company.smartsupport.common.PageResult;
import com.company.smartsupport.knowledge.dto.KnowledgeBlockDto;
import com.company.smartsupport.knowledge.dto.KnowledgeCandidatesResponse;
import com.company.smartsupport.knowledge.dto.KnowledgeIngestionTaskDto;
import com.company.smartsupport.knowledge.dto.KnowledgeSourceDto;

@Service
public class KnowledgeSourceService {

    private final KnowledgeRepository knowledgeRepository;

    public KnowledgeSourceService(KnowledgeRepository knowledgeRepository) {
        this.knowledgeRepository = knowledgeRepository;
    }

    public KnowledgeSourceDto createKnowledgeSource(Map<String, Object> body, String projectId) {
        KnowledgeSourceDto source = knowledgeRepository.createSource(body, projectId);
        knowledgeRepository.createTask(source.sourceId(), "parse");
        return source;
    }

    public PageResult<KnowledgeSourceDto> listKnowledgeSources(String projectId, int pageNo, int pageSize) {
        return PageResult.of(knowledgeRepository.listSources(projectId), pageNo, pageSize);
    }

    public PageResult<KnowledgeIngestionTaskDto> listTasks(String projectId, String sourceId, int pageNo, int pageSize) {
        return PageResult.of(knowledgeRepository.listTasks(projectId, sourceId), pageNo, pageSize);
    }

    public PageResult<KnowledgeBlockDto> listBlocks(String projectId, String sourceId, int pageNo, int pageSize) {
        return PageResult.of(knowledgeRepository.listBlocks(projectId, sourceId), pageNo, pageSize);
    }

    public KnowledgeCandidatesResponse getCandidates(String projectId, String sourceId) {
        return knowledgeRepository.getCandidates(projectId, sourceId);
    }

    public KnowledgeIngestionTaskDto createAction(String projectId, String sourceId, Map<String, Object> body) {
        Object action = body.get("action");
        return knowledgeRepository.createActionTask(projectId, sourceId, action == null ? "" : String.valueOf(action));
    }

    public KnowledgeIngestionTaskDto retry(String projectId, String sourceId) {
        return knowledgeRepository.createActionTask(projectId, sourceId, "retry");
    }
}
