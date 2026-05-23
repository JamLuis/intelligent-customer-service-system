package com.company.smartsupport.integration;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class McpServerClient {

    private final RestClient restClient;

    public McpServerClient(@Value("${mcp.server.base-url:http://localhost:3202}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    public Optional<List<Map<String, Object>>> tools() {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/tools")
                    .retrieve()
                    .body(Map.class);
            Object tools = response == null ? null : response.get("tools");
            if (tools instanceof List<?> list) {
                return Optional.of((List<Map<String, Object>>) list);
            }
            return Optional.empty();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> invoke(String capabilityCode, Map<String, Object> request) {
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/tools/{capabilityCode}/invoke", capabilityCode)
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
