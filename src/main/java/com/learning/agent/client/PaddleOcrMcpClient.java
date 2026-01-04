package com.learning.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learning.agent.config.AppConfigProperties;
import com.learning.agent.dto.client.OcrStructuredResult;
import com.learning.agent.dto.client.OcrTextSpan;
import com.learning.agent.util.McpConfigLoader;
import com.learning.agent.util.McpConfigLoader.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PaddleOCR MCP 客户端实现
 * 通过 MCP 协议与 PaddleOCR 服务交互
 */
@Slf4j
@Component
public class PaddleOcrMcpClient implements PaddleOcrClient {

    private final ObjectMapper objectMapper;
    private final McpConfigLoader configLoader;
    private final AppConfigProperties appConfig;

    private Process mcpProcess;
    private BufferedWriter processWriter;
    private BufferedReader processReader;
    private String currentPipeline; // OCR, PP-StructureV3, or PaddleOCR-VL
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean connected = false;

    public PaddleOcrMcpClient(ObjectMapper objectMapper, McpConfigLoader configLoader, AppConfigProperties appConfig) {
        this.objectMapper = objectMapper;
        this.configLoader = configLoader;
        this.appConfig = appConfig;
    }

    @Override
    public OcrStructuredResult runStructuredOcr(String imagePath) {
        String normalizedPath = Paths.get(imagePath).toAbsolutePath().toString();
        log.debug("PaddleOcrMcpClient.runStructuredOcr: {}", normalizedPath);

        try {
            // 确保连接已建立，以便 currentPipeline 被正确设置
            ensureConnected();

            Map<String, Object> args = new HashMap<>();
            args.put("input_data", normalizedPath);
            args.put("output_mode", "detailed");

            String toolName = getToolNameForPipeline(currentPipeline);
            log.debug("Using tool '{}' for pipeline '{}'", toolName, currentPipeline);

            JsonNode result = callToolWithRetry(toolName, args);
            JsonNode rawPayload = parseDetailedResult(result);
            return toStructuredResult(rawPayload, normalizedPath);
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("OCR failed: {}", errorMsg, e);
            return OcrStructuredResult.failure(normalizedPath, errorMsg);
        }
    }

    private JsonNode callToolWithRetry(String name, Map<String, Object> args) throws Exception {
        Exception lastError = null;

        for (int attempt = 1; attempt <= appConfig.getPaddleOcrRequestRetries(); attempt++) {
            int timeout = appConfig.getPaddleOcrRequestTimeoutMs() * attempt;
            try {
                return callTool(name, args, timeout);
            } catch (Exception e) {
                lastError = e;
                log.warn("PaddleOcrMcpClient.callTool attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < appConfig.getPaddleOcrRequestRetries()) {
                    // 指数退避等待
                    Thread.sleep(1000L * attempt);
                }
            }
        }

        if (lastError != null) {
            throw lastError;
        }
        throw new RuntimeException("MCP connection failed after " + appConfig.getPaddleOcrRequestRetries() + " attempts");
    }

    private synchronized JsonNode callTool(String name, Map<String, Object> args, int timeoutMs) throws Exception {
        ensureConnected();

        int requestId = requestIdCounter.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("jsonrpc", "2.0");
            request.put("id", requestId);
            request.put("method", "tools/call");

            ObjectNode params = objectMapper.createObjectNode();
            params.put("name", name);
            params.set("arguments", objectMapper.valueToTree(args));
            request.set("params", params);

            String requestStr = objectMapper.writeValueAsString(request);
            log.debug("Sending OCR MCP request: {}", requestStr);

            processWriter.write(requestStr);
            processWriter.newLine();
            processWriter.flush();

            JsonNode response = future.get(timeoutMs, TimeUnit.MILLISECONDS);

            if (response.has("error")) {
                throw new RuntimeException("MCP Error: " + response.get("error").toString());
            }

            JsonNode result = response.get("result");
            if (result != null && result.has("isError") && result.get("isError").asBoolean()) {
                StringBuilder message = new StringBuilder();
                if (result.has("content")) {
                    for (JsonNode item : result.get("content")) {
                        if ("text".equals(item.get("type").asText())) {
                            message.append(item.get("text").asText()).append("\n");
                        }
                    }
                }
                throw new RuntimeException("PaddleOCR MCP 调用失败: " +
                        (!message.isEmpty() ? message.toString() : "未知错误"));
            }

            return result;
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    private void ensureConnected() throws Exception {
        if (connected && mcpProcess != null && mcpProcess.isAlive()) {
            return;
        }

        initializeConnection();
    }

    private void initializeConnection() throws Exception {
        McpServerConfig config = configLoader.getServerConfig(appConfig.getPaddleOcrMcpServer());

        if (config.command() == null) {
            throw new RuntimeException("PaddleOCR MCP config must be command-based (stdio)");
        }

        List<String> command = new ArrayList<>();
        command.add(config.command());
        if (config.args() != null) {
            command.addAll(config.args());
        }

        ProcessBuilder pb = new ProcessBuilder(command);

        // 设置环境变量并读取 pipeline 配置
        Map<String, String> env = pb.environment();
        if (config.env() != null) {
            config.env().forEach((key, value) -> {
                String resolvedValue;
                if (value.startsWith("${") && value.endsWith("}")) {
                    String varName = value.substring(2, value.length() - 1);
                    // 支持 ${VAR:default} 格式
                    String[] parts = varName.split(":", 2);
                    String actualVarName = parts[0];
                    String defaultValue = parts.length > 1 ? parts[1] : "";
                    resolvedValue = System.getenv(actualVarName) != null ? System.getenv(actualVarName) : defaultValue;
                } else {
                    resolvedValue = value;
                }
                env.put(key, resolvedValue);

                // 记录 pipeline 类型
                if ("PADDLEOCR_MCP_PIPELINE".equals(key)) {
                    currentPipeline = resolvedValue;
                }
            });
        }

        // 如果没有从配置读取到，尝试从系统环境变量读取
        if (currentPipeline == null || currentPipeline.isEmpty()) {
            currentPipeline = System.getenv("PADDLEOCR_MCP_PIPELINE");
        }
        // 默认使用 PP-StructureV3
        if (currentPipeline == null || currentPipeline.isEmpty()) {
            currentPipeline = "PP-StructureV3";
        }

        log.info("PaddleOCR MCP Pipeline: {}", currentPipeline);

        if (config.workingDirectory() != null) {
            pb.directory(new File(config.workingDirectory()));
        }

        pb.redirectErrorStream(false);
        log.info("Starting PaddleOCR MCP process: {}", String.join(" ", command));
        mcpProcess = pb.start();

        processWriter = new BufferedWriter(new OutputStreamWriter(mcpProcess.getOutputStream()));
        processReader = new BufferedReader(new InputStreamReader(mcpProcess.getInputStream()));
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(mcpProcess.getErrorStream()));

        // 启动响应读取线程
        Thread readerThread = new Thread(this::readResponses);
        readerThread.setDaemon(true);
        readerThread.setName("mcp-stdout-reader");
        readerThread.start();

        // 启动错误流读取线程
        Thread errorReaderThread = new Thread(() -> readErrorStream(errorReader));
        errorReaderThread.setDaemon(true);
        errorReaderThread.setName("mcp-stderr-reader");
        errorReaderThread.start();

        // 发送初始化请求
        sendInitialize();

        connected = true;
        log.info("PaddleOCR MCP connected");
    }

    private void sendInitialize() throws Exception {
        int requestId = requestIdCounter.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", "initialize");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "learning-agent-paddleocr-client");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        params.set("capabilities", objectMapper.createObjectNode());
        request.set("params", params);

        log.debug("Sending initialize request: {}", request);
        processWriter.write(objectMapper.writeValueAsString(request));
        processWriter.newLine();
        processWriter.flush();

        log.info("Waiting for initialize response (timeout: {}s)...", appConfig.getPaddleOcrMcpInitTimeoutSec());
        try {
            future.get(appConfig.getPaddleOcrMcpInitTimeoutSec(), TimeUnit.SECONDS);
            log.info("Initialize response received");
        } catch (Exception e) {
            boolean isAlive = mcpProcess != null && mcpProcess.isAlive();
            log.error("Initialize failed - Process alive: {}, Error: {}", isAlive, e.getMessage());
            throw new RuntimeException("PaddleOCR MCP initialize timeout (" + appConfig.getPaddleOcrMcpInitTimeoutSec() + "s). Process alive: " + isAlive +
                    ". Check logs for stderr output. Ensure paddleocr_mcp is installed and configured correctly.", e);
        }
        pendingRequests.remove(requestId);

        // 发送 initialized 通知
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        processWriter.write(objectMapper.writeValueAsString(notification));
        processWriter.newLine();
        processWriter.flush();
    }

    private void readResponses() {
        try {
            String line;
            while ((line = processReader.readLine()) != null) {
                log.debug("OCR MCP raw response: {}", line);
                try {
                    JsonNode response = objectMapper.readTree(line);
                    if (response.has("id")) {
                        int id = response.get("id").asInt();
                        CompletableFuture<JsonNode> future = pendingRequests.get(id);
                        if (future != null) {
                            future.complete(response);
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse OCR MCP response: {}", line);
                }
            }
        } catch (IOException e) {
            log.error("Error reading OCR MCP responses", e);
        }
    }

    private void readErrorStream(BufferedReader errorReader) {
        try {
            String line;
            while ((line = errorReader.readLine()) != null) {
                log.warn("OCR MCP stderr: {}", line);
            }
        } catch (IOException e) {
            log.error("Error reading OCR MCP error stream", e);
        }
    }

    /**
     * 根据 pipeline 类型返回对应的 MCP 工具名称
     *
     * @param pipeline Pipeline 类型 (OCR, PP-StructureV3, PaddleOCR-VL)
     * @return MCP 工具名称
     */
    private String getToolNameForPipeline(String pipeline) {
        if (pipeline == null || pipeline.isEmpty()) {
            log.warn("Pipeline not specified, defaulting to pp_structurev3");
            return "pp_structurev3";
        }

        return switch (pipeline) {
            case "OCR" -> "ocr";
            case "PP-StructureV3" -> "pp_structurev3";
            case "PaddleOCR-VL" -> "paddleocr_vl";
            default -> {
                log.warn("Unknown pipeline '{}', defaulting to pp_structurev3", pipeline);
                yield "pp_structurev3";
            }
        };
    }

    private JsonNode parseDetailedResult(JsonNode result) {
        if (result == null || !result.has("content")) {
            throw new RuntimeException("PaddleOCR MCP 未返回内容");
        }

        List<String> textSegments = new ArrayList<>();
        for (JsonNode item : result.get("content")) {
            if ("text".equals(item.get("type").asText()) && item.has("text")) {
                String text = item.get("text").asText().trim();
                if (!text.isEmpty()) {
                    textSegments.add(text);
                }
            }
        }

        if (textSegments.isEmpty()) {
            throw new RuntimeException("PaddleOCR MCP 未返回文本内容");
        }

        // PaddleOCR 返回两个 text 内容：
        // 1. 第一个是纯文本（markdown格式）
        // 2. 第二个是详细的JSON结构
        String plainText = textSegments.getFirst();

        // 从后往前查找 JSON 片段
        String jsonCandidate = null;
        for (int i = textSegments.size() - 1; i >= 0; i--) {
            String segment = textSegments.get(i);
            if (segment.startsWith("{") || segment.startsWith("[")) {
                jsonCandidate = segment;
                break;
            }
        }

        if (jsonCandidate == null) {
            throw new RuntimeException("PaddleOCR MCP detailed 输出不包含 JSON 片段");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(jsonCandidate);
            // 将纯文本添加到JSON结构中，以便后续使用
            if (jsonNode.isObject() && !plainText.isEmpty()) {
                ((ObjectNode) jsonNode).put("text", plainText);
            }
            return jsonNode;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("无法解析 PaddleOCR MCP 输出: " + e.getMessage());
        }
    }

    private OcrStructuredResult toStructuredResult(JsonNode raw, String sourcePath) {
        String plainText = raw.has("text") ? raw.get("text").asText().trim() : "";

        List<OcrTextSpan> spans = new ArrayList<>();
        if (raw.has("text_lines")) {
            int index = 0;
            for (JsonNode line : raw.get("text_lines")) {
                double confidence = line.has("confidence") ? line.get("confidence").asDouble() :
                        (raw.has("confidence") ? raw.get("confidence").asDouble() : 0);

                List<Double> boundingBox = new ArrayList<>();
                if (line.has("bbox")) {
                    for (JsonNode coord : line.get("bbox")) {
                        boundingBox.add(coord.asDouble());
                    }
                }

                OcrTextSpan span = OcrTextSpan.builder()
                        .lineId("line-" + index)
                        .text(line.has("text") ? line.get("text").asText().trim() : "")
                        .confidence(confidence)
                        .boundingBox(boundingBox)
                        .classification(index == 0 ? "question" : "analysis")
                        .sourceMeta(Map.of("confidence", String.format("%.3f", confidence)))
                        .build();

                spans.add(span);
                index++;
            }
        }

        // 构建 Markdown 文本
        List<String> lines = !plainText.isEmpty()
                ? Arrays.asList(plainText.split("\n"))
                : spans.stream().map(OcrTextSpan::getText).toList();

        String markdownText = lines.stream()
                .filter(l -> !l.isEmpty())
                .map(l -> "- " + l)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        // 构建表格数据
        List<List<String>> tableData = new ArrayList<>();
        tableData.add(List.of("#", "内容", "置信度"));
        for (int i = 0; i < spans.size(); i++) {
            OcrTextSpan span = spans.get(i);
            tableData.add(List.of(
                    String.valueOf(i + 1),
                    span.getText(),
                    span.getConfidence() > 0 ? String.format("%.1f%%", span.getConfidence() * 100) : "-"
            ));
        }

        return OcrStructuredResult.builder()
                .success(true)
                .originalPath(sourcePath)
                .plainText(plainText)
                .markdownText(markdownText)
                .tableData(tableData)
                .spans(spans)
                .build();
    }
}
