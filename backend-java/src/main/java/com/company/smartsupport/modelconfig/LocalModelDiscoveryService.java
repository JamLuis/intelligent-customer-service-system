package com.company.smartsupport.modelconfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class LocalModelDiscoveryService {

    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {
    };

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LocalModelDiscoveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public Map<String, Object> discover(String purpose) {
        List<Map<String, Object>> runtimes = new ArrayList<>();
        List<Map<String, Object>> models = new ArrayList<>();
        discoverOpenAiCompatible("mlx", "MLX", "http://127.0.0.1:18090/v1", purpose, runtimes, models);
        discoverOllama(purpose, runtimes, models);
        discoverOpenAiCompatible("llama.cpp", "Gemma / llama.cpp", "http://127.0.0.1:11435/v1", purpose, runtimes, models);
        discoverOpenAiCompatible("lm-studio", "LM Studio", "http://127.0.0.1:1234/v1", purpose, runtimes, models);
        return Map.of(
                "runtimes", runtimes,
                "models", models,
                "total", models.size());
    }

    @SuppressWarnings("unchecked")
    private void discoverOllama(String purpose, List<Map<String, Object>> runtimes, List<Map<String, Object>> models) {
        String apiBaseUrl = "http://127.0.0.1:11434/v1";
        try {
            Map<String, Object> payload = getJson("http://127.0.0.1:11434/api/tags");
            runtimes.add(runtime("ollama", "Ollama", apiBaseUrl, true, "已发现本地 Ollama 服务"));
            Object rawModels = payload.get("models");
            if (rawModels instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    Map<String, Object> model = (Map<String, Object>) raw;
                    String name = text(model.get("name"));
                    if (name.isBlank()) {
                        continue;
                    }
                    models.add(modelOption(
                            "ollama",
                            "Ollama",
                            "ollama-openai-compatible",
                            apiBaseUrl,
                            name,
                            text(model.get("model")),
                            text(model.get("modified_at")),
                            sizeLabel(model.get("size")),
                            "ollama",
                            "ollama",
                            purpose));
                }
            }
        } catch (RuntimeException ex) {
            runtimes.add(runtime("ollama", "Ollama", apiBaseUrl, false, "未检测到 Ollama：" + readableMessage(ex)));
        }
    }

    @SuppressWarnings("unchecked")
    private void discoverOpenAiCompatible(String runtime, String displayName, String apiBaseUrl, String purpose,
            List<Map<String, Object>> runtimes, List<Map<String, Object>> models) {
        try {
            Map<String, Object> payload = getJson(apiBaseUrl + "/models");
            runtimes.add(runtime(runtime, displayName, apiBaseUrl, true, "已发现 " + displayName + " 服务"));
            Object data = payload.get("data");
            if (data instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    Map<String, Object> model = (Map<String, Object>) raw;
                    String id = text(model.get("id"));
                    if (id.isBlank()) {
                        id = text(model.get("model"));
                    }
                    if (id.isBlank()) {
                        continue;
                    }
                    models.add(modelOption(
                            runtime,
                            displayName,
                            runtime.equals("llama.cpp") ? "local-openai-compatible" : runtime + "-openai-compatible",
                            apiBaseUrl,
                            id,
                            id,
                            text(model.get("created")),
                            "",
                            "",
                            runtime.equals("llama.cpp") ? "llama" : runtime,
                            purpose));
                }
            }
        } catch (RuntimeException ex) {
            runtimes.add(runtime(runtime, displayName, apiBaseUrl, false, "未检测到 " + displayName + "：" + readableMessage(ex)));
        }
    }

    private Map<String, Object> runtime(String runtime, String displayName, String apiBaseUrl, boolean available, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtime", runtime);
        result.put("displayName", displayName);
        result.put("apiBaseUrl", apiBaseUrl);
        result.put("available", available);
        result.put("message", message);
        return result;
    }

    private Map<String, Object> modelOption(String runtime, String runtimeLabel, String provider, String apiBaseUrl,
            String modelName, String modelId, String modifiedAt, String sizeLabel, String apiKey, String localRuntime, String purpose) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtime", runtime);
        result.put("runtimeLabel", runtimeLabel);
        result.put("provider", provider);
        result.put("apiBaseUrl", apiBaseUrl);
        result.put("apiKey", apiKey);
        result.put("model", modelName);
        result.put("modelId", modelId);
        result.put("displayName", displayName(purpose, runtimeLabel, modelName));
        result.put("profileKey", profileKey(runtime, modelName));
        result.put("localRuntime", localRuntime);
        result.put("modifiedAt", modifiedAt);
        result.put("sizeLabel", sizeLabel);
        result.put("contextWindow", 8192);
        result.put("temperature", "knowledge_extract".equals(purpose) ? 0 : 0.2);
        result.put("maxTokens", defaultMaxTokens(purpose, modelName));
        result.put("forceGraphGrounding", true);
        result.put("embeddingModel", "mlx-community/bge-m3-mlx-4bit");
        result.put("embeddingDim", 1024);
        result.put("recommended", recommended(modelName));
        result.put("fitNote", fitNote(modelName));
        return result;
    }

    private Map<String, Object> getJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }
            return objectMapper.readValue(response.body(), OBJECT_MAP);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("扫描被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException(readableMessage(ex), ex);
        }
    }

    private String readableMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "服务未启动或端口不可访问" : message;
    }

    private String displayName(String purpose, String runtimeLabel, String modelName) {
        return ("knowledge_extract".equals(purpose) ? "知识抽取" : "对话问答") + " - " + runtimeLabel + " " + modelName;
    }

    private String profileKey(String runtime, String modelName) {
        String normalized = (runtime + "-" + modelName).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.length() > 60) {
            normalized = normalized.substring(0, 60).replaceAll("-+$", "");
        }
        return normalized.isBlank() ? "local-model" : normalized;
    }

    private int defaultMaxTokens(String purpose, String modelName) {
        if ("knowledge_extract".equals(purpose)) {
            return 900;
        }
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("1.5b") || lower.contains("1_5b")) {
            return 384;
        }
        if (lower.contains("3b")) {
            return 512;
        }
        return 768;
    }

    private boolean recommended(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        return lower.contains("qwen2.5:1.5b") || lower.contains("qwen2.5:3b") || lower.contains("qwen");
    }

    private String fitNote(String modelName) {
        String lower = modelName.toLowerCase(Locale.ROOT);
        if (lower.contains("qwen2.5:1.5b")) {
            return "速度优先，适合本地知识库问答和确定性证据回答";
        }
        if (lower.contains("qwen2.5:3b")) {
            return "能力略强但延迟更高，建议先验证后启用";
        }
        if (lower.contains("qwen3.5") && lower.contains("4bit")) {
            return "Apple Silicon 上速度优先，适合本地知识抽取和结构化 JSON 输出";
        }
        if (lower.contains("gemma")) {
            return "可用于实验，长 RAG prompt 可能较慢";
        }
        return "已发现本地模型，建议先验证连接再启用";
    }

    private String sizeLabel(Object value) {
        if (!(value instanceof Number number)) {
            return "";
        }
        double bytes = number.doubleValue();
        if (bytes <= 0) {
            return "";
        }
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / 1024 / 1024 / 1024);
        }
        return String.format(Locale.ROOT, "%.0f MB", bytes / 1024 / 1024);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
