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

    public AiServiceClient(@Value("${ai.service.base-url:http://localhost:8100}") String baseUrl) {
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
