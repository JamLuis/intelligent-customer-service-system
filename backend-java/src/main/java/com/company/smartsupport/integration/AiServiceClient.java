package com.company.smartsupport.integration;

import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiServiceClient {

    private final RestClient restClient;

    public AiServiceClient(@Value("${smart-support.ai-service-base-url:${ai.service.base-url:http://localhost:8100}}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> runDiagnosis(Map<String, Object> request) {
        return post("/diagnosis/run", request);
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> ingestKnowledge(Map<String, Object> request) {
        return post("/knowledge/ingest", request);
    }

    public Optional<Map<String, Object>> parseKnowledge(Map<String, Object> request) {
        return post("/knowledge/parse", request);
    }

    public Optional<Map<String, Object>> extractKnowledge(Map<String, Object> request) {
        return post("/knowledge/extract", request);
    }

    public Optional<Map<String, Object>> embedKnowledge(Map<String, Object> request) {
        return post("/knowledge/embed", request);
    }

    public Optional<Map<String, Object>> checkLlm(Map<String, Object> request) {
        return post("/llm/check", request);
    }

    public Optional<Map<String, Object>> answerChat(Map<String, Object> request) {
        return post("/chat/answer", request);
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> queryGraphs(Map<String, Object> request) {
        return post("/graphs/query", request);
    }

    private Optional<Map<String, Object>> post(String path, Map<String, Object> request) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            return Optional.ofNullable(response);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
