package com.company.smartsupport.modelconfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LocalModelRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(LocalModelRuntimeService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public Map<String, Object> start(String runtime) {
        String normalized = normalizeRuntime(runtime);
        return switch (normalized) {
            case "ollama" -> startOllama();
            case "llama.cpp" -> startLlamaCpp();
            case "lm-studio" -> runtimeResult(normalized, false, false, "LM Studio 需要手动从桌面应用启动", "http://127.0.0.1:1234/v1");
            default -> runtimeResult(normalized, false, false, "不支持的运行时: " + runtime, "");
        };
    }

    private Map<String, Object> startOllama() {
        String baseUrl = "http://127.0.0.1:11434/v1";
        if (isAvailable("http://127.0.0.1:11434/api/tags")) {
            return runtimeResult("ollama", true, false, "Ollama 已在运行", baseUrl);
        }
        Path root = findProjectRoot();
        String command = "nohup env OLLAMA_HOST=127.0.0.1:11434 ollama serve > .runtime/logs/ollama-manual.log 2>&1 &";
        execute(root, command);
        boolean available = waitAvailable("http://127.0.0.1:11434/api/tags", Duration.ofSeconds(20));
        return runtimeResult("ollama", available, true,
                available ? "Ollama 启动成功" : "已触发启动命令，但未在 20 秒内就绪",
                baseUrl);
    }

    private Map<String, Object> startLlamaCpp() {
        String baseUrl = "http://127.0.0.1:11435/v1";
        if (isAvailable(baseUrl + "/models")) {
            return runtimeResult("llama.cpp", true, false, "llama.cpp 已在运行", baseUrl);
        }
        Path root = findProjectRoot();
        Path script = root.resolve("scripts/gemma-llamacpp.sh");
        if (!Files.exists(script)) {
            return runtimeResult("llama.cpp", false, false, "未找到启动脚本: scripts/gemma-llamacpp.sh", baseUrl);
        }
        execute(root, "./scripts/gemma-llamacpp.sh start");
        boolean available = waitAvailable(baseUrl + "/models", Duration.ofSeconds(30));
        return runtimeResult("llama.cpp", available, true,
                available ? "llama.cpp 启动成功" : "已触发启动脚本，但未在 30 秒内就绪",
                baseUrl);
    }

    private void execute(Path workingDir, String command) {
        ProcessBuilder pb = new ProcessBuilder("bash", "-lc", command);
        pb.directory(workingDir.toFile());
        try {
            Process process = pb.start();
            boolean finished = process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() != 0) {
                String err = new String(process.getErrorStream().readAllBytes());
                throw new IllegalStateException(err.isBlank() ? "启动命令执行失败" : err.trim());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("执行启动命令失败: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("执行启动命令被中断", ex);
        }
    }

    private boolean isAvailable(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 400;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean waitAvailable(String url, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (isAvailable(url)) {
                return true;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Preload a model in the runtime (Ollama: loads model into memory).
     */
    public Map<String, Object> preloadModel(String runtime, String apiBaseUrl, String model) {
        String normalized = normalizeRuntime(runtime);
        if ("ollama".equals(normalized)) {
            return ollamaModelKeepAlive(apiBaseUrl, model, "5m", "preload");
        }
        // llama.cpp / lm-studio: model is loaded at server start, no lazy load needed
        return runtimeResult(normalized, true, false, normalized + " 模型随服务加载，无需预热", apiBaseUrl);
    }

    /**
     * Unload a model from the runtime (Ollama: frees memory).
     */
    public Map<String, Object> unloadModel(String runtime, String apiBaseUrl, String model) {
        String normalized = normalizeRuntime(runtime);
        if ("ollama".equals(normalized)) {
            return ollamaModelKeepAlive(apiBaseUrl, model, "0", "unload");
        }
        return runtimeResult(normalized, true, false, normalized + " 不支持单独卸载模型", apiBaseUrl);
    }

    private Map<String, Object> ollamaModelKeepAlive(String apiBaseUrl, String model, String keepAlive, String action) {
        // Ollama API is on the base host (strip /v1 suffix)
        String ollamaBase = apiBaseUrl.replaceAll("/v1/?$", "");
        String jsonBody = "{\"model\":\"" + model + "\",\"keep_alive\":\"" + keepAlive + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaBase + "/api/generate"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() < 400;
            String msg = ok
                    ? ("preload".equals(action) ? "模型 " + model + " 已加载到内存" : "模型 " + model + " 已从内存卸载")
                    : "Ollama " + action + " 失败: HTTP " + response.statusCode();
            log.info("Ollama {} model={} keepAlive={} status={}", action, model, keepAlive, response.statusCode());
            return runtimeResult("ollama", ok, false, msg, apiBaseUrl);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return runtimeResult("ollama", false, false, "Ollama " + action + " 被中断", apiBaseUrl);
        } catch (Exception ex) {
            log.warn("Ollama {} failed for model={}: {}", action, model, ex.getMessage());
            return runtimeResult("ollama", false, false, "Ollama " + action + " 失败: " + ex.getMessage(), apiBaseUrl);
        }
    }

    private Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 6 && current != null; i++) {
            if (Files.exists(current.resolve("scripts/start-all.sh"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of(".").toAbsolutePath();
    }

    private String normalizeRuntime(String runtime) {
        String normalized = runtime == null ? "" : runtime.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("llama") || normalized.equals("llama-cpp")) {
            return "llama.cpp";
        }
        return normalized;
    }

    private Map<String, Object> runtimeResult(String runtime, boolean available, boolean startTriggered, String message, String apiBaseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runtime", runtime);
        result.put("available", available);
        result.put("startTriggered", startTriggered);
        result.put("message", message);
        result.put("apiBaseUrl", apiBaseUrl);
        return result;
    }
}
