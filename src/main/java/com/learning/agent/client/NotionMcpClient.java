package com.learning.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.NotionWritePayload;
import com.learning.agent.util.McpConfigLoader;
import com.learning.agent.util.McpConfigLoader.McpServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Notion MCP 客户端实现
 * 通过 MCP 协议与 Notion API 交互
 */
@Slf4j
@Component
public class NotionMcpClient implements NotionClient {

    private static final int BATCH_SIZE = 100;
    private static final int CALL_TIMEOUT_SECONDS = 30;
    private static final int MAX_RETRY_ATTEMPTS = 2;

    private final ObjectMapper objectMapper;
    private final McpConfigLoader configLoader;

    @Value("${notion.mcp.token}")
    private String authToken;

    @Value("${notion.mcp.version:2022-06-28}")
    private String notionVersion;

    private Process mcpProcess;
    private BufferedWriter processWriter;
    private BufferedReader processReader;
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final Map<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();
    private volatile boolean connected = false;

    public NotionMcpClient(ObjectMapper objectMapper, McpConfigLoader configLoader) {
        this.objectMapper = objectMapper;
        this.configLoader = configLoader;
    }

    @PostConstruct
    public void init() {
        if (authToken == null || authToken.isEmpty()) {
            log.warn("NOTION_MCP_TOKEN 未设置，Notion 客户端功能将不可用");
            return;
        }
        if (!authToken.startsWith("ntn_")) {
            log.warn("NOTION_MCP_TOKEN 不以 'ntn_' 开头，可能使用了错误的令牌类型");
        }
    }

    @Override
    public NotionCreatedPage createPage(NotionWritePayload payload) {
        log.debug("NotionMcpClient.createPage: {}", payload.getTitle());

        // 转换 markdownContent 为 Notion Blocks
        List<Map<String, Object>> children = convertMarkdownToBlocks(payload.getMarkdownContent());

        // 附加元数据到内容顶部
        if (payload.getProperties() != null && !payload.getProperties().isEmpty()) {
            Map<String, Object> calloutBlock = createCalloutBlock(payload.getProperties());
            children.addFirst(calloutBlock);
        }

        // 创建页面参数
        Map<String, Object> args = getArgs(payload);

        log.debug("Calling API-post-page with args: {}", args);
        JsonNode result = callTool("API-post-page", args);

        String newPageId = extractResourceIdentifier(result);
        if (newPageId == null) {
            throw new RuntimeException("Failed to create page: No page ID returned from Notion API");
        }

        log.debug("Created page {}, now appending content...", newPageId);

        // 分批追加内容
        try {
            for (int i = 0; i < children.size(); i += BATCH_SIZE) {
                List<Map<String, Object>> batch = children.subList(i, Math.min(i + BATCH_SIZE, children.size()));
                appendBlockChildren(newPageId, batch);
            }
            log.debug("Content appended successfully");
        } catch (Exception e) {
            log.error("Failed to append initial content to new page", e);
        }

        String pageUrl = extractUrl(result);
        if (pageUrl != null) {
            log.info("\n✨ Notion 页面已创建！点击链接直接打开:\n👉 {}\n", pageUrl);
        }

        return NotionCreatedPage.builder()
                .id(newPageId)
                .url(pageUrl)
                .build();
    }

    @NotNull
    private static Map<String, Object> getArgs(NotionWritePayload payload) {
        Map<String, Object> args = new HashMap<>();
        Map<String, Object> parent = new HashMap<>();
        parent.put("page_id", payload.getParentPageId());
        args.put("parent", parent);

        Map<String, Object> properties = new HashMap<>();
        List<Map<String, Object>> titleContent = new ArrayList<>();
        Map<String, Object> textMap = new HashMap<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", payload.getTitle());
        textMap.put("text", textContent);
        titleContent.add(textMap);
        properties.put("title", titleContent);
        args.put("properties", properties);
        return args;
    }

    @Override
    public void updatePage(String pageId, Map<String, Object> properties) {
        log.debug("NotionMcpClient.updatePage: {}", pageId);
        
        Map<String, Object> args = new HashMap<>();
        args.put("page_id", pageId);
        
        // 如果 properties 包含 archived 字段，直接传递
        // 否则需要将其放入 properties 结构中
        if (properties.containsKey("archived")) {
            args.put("archived", properties.get("archived"));
        }
        
        // 其他属性需要放入 properties 字段
        Map<String, Object> otherProps = new HashMap<>(properties);
        otherProps.remove("archived"); // 移除 archived，因为它是顶级字段
        
        if (!otherProps.isEmpty()) {
            args.put("properties", otherProps);
        }
        
        try {
            callTool("API-patch-page", args);
            log.debug("Page updated successfully: {}", pageId);
        } catch (Exception e) {
            log.error("Failed to update page: {}", pageId, e);
            throw new RuntimeException("Failed to update page: " + pageId, e);
        }
    }

    @Override
    public void createComment(String pageId, String commentText) {
        log.debug("NotionMcpClient.createComment: {}", pageId);
        try {
            Map<String, Object> args = new HashMap<>();
            Map<String, Object> parent = new HashMap<>();
            parent.put("page_id", pageId);
            args.put("parent", parent);

            List<Map<String, Object>> richText = new ArrayList<>();
            Map<String, Object> textMap = new HashMap<>();
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("content", commentText);
            textMap.put("text", textContent);
            richText.add(textMap);
            args.put("rich_text", richText);

            callTool("API-create-a-comment", args);
        } catch (Exception e) {
            log.warn("Failed to create comment (likely permission issue), skipping.", e);
        }
    }

    @Override
    public Optional<SearchResult> searchPage(String query) {
        log.debug("NotionMcpClient.searchPage: {}", query);
        Map<String, Object> args = new HashMap<>();
        args.put("query", query);
        Map<String, Object> filter = new HashMap<>();
        filter.put("value", "page");
        filter.put("property", "object");
        args.put("filter", filter);
        args.put("page_size", 1);

        try {
            JsonNode result = callTool("API-post-search", args);
            JsonNode parsed = parseResult(result);

            if (parsed != null && parsed.has("results") && parsed.get("results").isArray()) {
                ArrayNode results = (ArrayNode) parsed.get("results");
                if (!results.isEmpty()) {
                    JsonNode page = results.get(0);
                    String title = "Untitled";

                    if (page.has("properties")) {
                        JsonNode properties = page.get("properties");
                        Iterator<String> fieldNames = properties.fieldNames();
                        while (fieldNames.hasNext()) {
                            String key = fieldNames.next();
                            JsonNode prop = properties.get(key);
                            if (prop.has("type") && "title".equals(prop.get("type").asText())) {
                                JsonNode titleArray = prop.get("title");
                                if (titleArray != null && titleArray.isArray()) {
                                    StringBuilder sb = new StringBuilder();
                                    for (JsonNode t : titleArray) {
                                        if (t.has("plain_text")) {
                                            sb.append(t.get("plain_text").asText());
                                        }
                                    }
                                    title = sb.toString();
                                }
                                break;
                            }
                        }
                    }

                    return Optional.of(new SearchResult(page.get("id").asText(), title));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("searchPage failed", e);
            return Optional.empty();
        }
    }

    // --- Expanded Capabilities Implementation ---

    @Override
    public Object getUser(String userId) {
        Map<String, Object> args = new HashMap<>();
        args.put("user_id", userId);
        return parseResult(callTool("API-get-user", args));
    }

    @Override
    public Object listUsers(Integer pageSize, String startCursor) {
        Map<String, Object> args = new HashMap<>();
        if (pageSize != null) args.put("page_size", pageSize);
        if (startCursor != null) args.put("start_cursor", startCursor);
        return parseResult(callTool("API-get-users", args));
    }

    @Override
    public Object getSelf() {
        return parseResult(callTool("API-get-self", new HashMap<>()));
    }

    @Override
    public Object queryDatabase(String databaseId, Object filter, Object sorts, Integer pageSize, String startCursor) {
        Map<String, Object> args = new HashMap<>();
        args.put("database_id", databaseId);
        if (filter != null) args.put("filter", filter);
        if (sorts != null) args.put("sorts", sorts);
        if (pageSize != null) args.put("page_size", pageSize);
        if (startCursor != null) args.put("start_cursor", startCursor);
        return parseResult(callTool("API-post-database-query", args));
    }

    @Override
    public Object search(String query, Object filter, Object sort, Integer pageSize, String startCursor) {
        Map<String, Object> args = new HashMap<>();
        args.put("query", query);
        if (filter != null) args.put("filter", filter);
        if (sort != null) args.put("sort", sort);
        if (pageSize != null) args.put("page_size", pageSize);
        if (startCursor != null) args.put("start_cursor", startCursor);
        return parseResult(callTool("API-post-search", args));
    }

    @Override
    public Object getBlockChildren(String blockId, Integer pageSize, String startCursor) {
        Map<String, Object> args = new HashMap<>();
        args.put("block_id", blockId);
        if (pageSize != null) args.put("page_size", pageSize);
        if (startCursor != null) args.put("start_cursor", startCursor);
        return parseResult(callTool("API-get-block-children", args));
    }

    @Override
    public Object appendBlockChildren(String blockId, List<Map<String, Object>> children) {
        Map<String, Object> args = new HashMap<>();
        args.put("block_id", blockId);
        args.put("children", children);
        return parseResult(callTool("API-patch-block-children", args));
    }

    @Override
    public Object retrieveBlock(String blockId) {
        Map<String, Object> args = new HashMap<>();
        args.put("block_id", blockId);
        return parseResult(callTool("API-retrieve-a-block", args));
    }

    @Override
    public Object updateBlock(String blockId, Map<String, Object> block) {
        Map<String, Object> args = new HashMap<>(block);
        args.put("block_id", blockId);
        return parseResult(callTool("API-update-a-block", args));
    }

    @Override
    public Object deleteBlock(String blockId) {
        Map<String, Object> args = new HashMap<>();
        args.put("block_id", blockId);
        return parseResult(callTool("API-delete-a-block", args));
    }

    @Override
    public Object retrievePage(String pageId) {
        Map<String, Object> args = new HashMap<>();
        args.put("page_id", pageId);
        return parseResult(callTool("API-retrieve-a-page", args));
    }

    @Override
    public Object createDatabase(Object parent, List<Object> title, Map<String, Object> properties) {
        Map<String, Object> args = new HashMap<>();
        args.put("parent", parent);
        args.put("title", title);
        args.put("properties", properties);
        return parseResult(callTool("API-create-a-database", args));
    }

    @Override
    public Object updateDatabase(String databaseId, Map<String, Object> properties) {
        Map<String, Object> args = new HashMap<>();
        args.put("database_id", databaseId);
        args.put("properties", properties);
        return parseResult(callTool("API-update-a-database", args));
    }

    @Override
    public Object retrieveDatabase(String databaseId) {
        Map<String, Object> args = new HashMap<>();
        args.put("database_id", databaseId);
        return parseResult(callTool("API-retrieve-a-database", args));
    }

    @Override
    public Object retrievePageProperty(String pageId, String propertyId) {
        Map<String, Object> args = new HashMap<>();
        args.put("page_id", pageId);
        args.put("property_id", propertyId);
        return parseResult(callTool("API-retrieve-a-page-property", args));
    }

    @Override
    public Object retrieveComments(String blockId, Integer pageSize, String startCursor) {
        Map<String, Object> args = new HashMap<>();
        args.put("block_id", blockId);
        if (pageSize != null) args.put("page_size", pageSize);
        if (startCursor != null) args.put("start_cursor", startCursor);
        return parseResult(callTool("API-retrieve-a-comment", args));
    }

    // --- Private Helper Methods ---

    private List<Map<String, Object>> convertMarkdownToBlocks(String markdownContent) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        String[] lines = markdownContent.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Map<String, Object> block = new HashMap<>();
            block.put("object", "block");

            if (line.startsWith("# ")) {
                block.put("type", "heading_1");
                block.put("heading_1", createRichText(line.substring(2)));
            } else if (line.startsWith("## ")) {
                block.put("type", "heading_2");
                block.put("heading_2", createRichText(line.substring(3)));
            } else if (line.startsWith("### ")) {
                block.put("type", "heading_3");
                block.put("heading_3", createRichText(line.substring(4)));
            } else if (line.startsWith("- ")) {
                block.put("type", "bulleted_list_item");
                block.put("bulleted_list_item", createRichText(line.substring(2)));
            } else if (line.matches("^\\d+\\.\\s.*")) {
                block.put("type", "numbered_list_item");
                block.put("numbered_list_item", createRichText(line.replaceFirst("^\\d+\\.\\s", "")));
            } else {
                block.put("type", "paragraph");
                block.put("paragraph", createRichText(line));
            }

            blocks.add(block);
        }

        return blocks;
    }

    private Map<String, Object> createRichText(String content) {
        Map<String, Object> richTextMap = new HashMap<>();
        List<Map<String, Object>> richText = new ArrayList<>();
        Map<String, Object> textObj = new HashMap<>();
        textObj.put("type", "text");
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("content", content);
        textObj.put("text", textContent);
        richText.add(textObj);
        richTextMap.put("rich_text", richText);
        return richTextMap;
    }

    private Map<String, Object> createCalloutBlock(Map<String, Object> properties) {
        StringBuilder metaInfo = new StringBuilder();
        properties.forEach((key, value) -> metaInfo.append(key).append(": ").append(value).append("\n"));

        Map<String, Object> block = new HashMap<>();
        block.put("object", "block");
        block.put("type", "callout");

        Map<String, Object> callout = new HashMap<>();
        callout.put("rich_text", List.of(Map.of(
                "type", "text",
                "text", Map.of("content", metaInfo.toString().trim())
        )));
        callout.put("icon", Map.of("emoji", "ℹ️"));

        block.put("callout", callout);
        return block;
    }

    private synchronized JsonNode callTool(String name, Map<String, Object> args) {
        return callToolWithRetry(name, args, 0);
    }

    private JsonNode callToolWithRetry(String name, Map<String, Object> args, int attempt) {
        ensureConnected();

        // 检查进程是否仍在运行
        if (mcpProcess == null || !mcpProcess.isAlive()) {
            log.warn("MCP process is not alive, attempting to reconnect...");
            connected = false;
            ensureConnected();
        }

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
            log.debug("Sending MCP request: {}", requestStr);

            processWriter.write(requestStr);
            processWriter.newLine();
            processWriter.flush();

            JsonNode response = future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Received MCP response: {}", response);

            if (response.has("error")) {
                throw new RuntimeException("MCP Error: " + response.get("error").toString());
            }

            return response.get("result");
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("MCP call timeout after {}s for tool: {}. Process alive: {}",
                    CALL_TIMEOUT_SECONDS, name, mcpProcess != null && mcpProcess.isAlive());

            // 尝试重连和重试
            if (attempt < MAX_RETRY_ATTEMPTS) {
                log.info("Retrying MCP call (attempt {}/{})", attempt + 1, MAX_RETRY_ATTEMPTS);
                connected = false;
                destroyProcess();
                return callToolWithRetry(name, args, attempt + 1);
            }

            throw new RuntimeException("MCP call timeout: " + name, e);
        } catch (Exception e) {
            log.error("MCP call failed for tool: {}", name, e);
            throw new RuntimeException("MCP call failed: " + name, e);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    private void destroyProcess() {
        if (mcpProcess != null) {
            try {
                mcpProcess.destroyForcibly();
                mcpProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mcpProcess = null;
        }
        if (processWriter != null) {
            try {
                processWriter.close();
            } catch (IOException ignored) {
            }
            processWriter = null;
        }
        if (processReader != null) {
            try {
                processReader.close();
            } catch (IOException ignored) {
            }
            processReader = null;
        }
    }

    private void ensureConnected() {
        if (connected && mcpProcess != null && mcpProcess.isAlive()) {
            return;
        }

        try {
            initializeConnection();
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to Notion MCP", e);
        }
    }

    private void initializeConnection() throws Exception {
        McpServerConfig config = configLoader.getServerConfig("notion");

        if (config.command() == null) {
            throw new RuntimeException("Notion MCP config must be command-based (stdio)");
        }

        List<String> command = new ArrayList<>();
        command.add(config.command());
        if (config.args() != null) {
            command.addAll(config.args());
        }

        ProcessBuilder pb = new ProcessBuilder(command);

        // 设置环境变量
        Map<String, String> env = pb.environment();
        if (config.env() != null) {
            config.env().forEach((key, value) -> {
                if (value.startsWith("${") && value.endsWith("}")) {
                    String varName = value.substring(2, value.length() - 1);
                    String envValue = System.getenv(varName);
                    // 只有当环境变量存在且非空时才设置
                    if (envValue != null && !envValue.isEmpty()) {
                        env.put(key, envValue);
                    }
                } else {
                    env.put(key, value);
                }
            });
        }
        // 使用 Spring 配置的 authToken 作为 NOTION_TOKEN 的 fallback
        if (authToken != null && !authToken.isEmpty()) {
            // 如果 NOTION_TOKEN 未设置或为空，使用 authToken
            if (!env.containsKey("NOTION_TOKEN") || env.get("NOTION_TOKEN").isEmpty()) {
                env.put("NOTION_TOKEN", authToken);
                log.debug("Using authToken from Spring config for NOTION_TOKEN");
            }
        }

        if (config.workingDirectory() != null) {
            pb.directory(new File(config.workingDirectory()));
        }

        pb.redirectErrorStream(false);
        mcpProcess = pb.start();

        processWriter = new BufferedWriter(new OutputStreamWriter(mcpProcess.getOutputStream()));
        processReader = new BufferedReader(new InputStreamReader(mcpProcess.getInputStream()));

        // 启动响应读取线程
        Thread readerThread = new Thread(this::readResponses);
        readerThread.setDaemon(true);
        readerThread.setName("NotionMCP-ResponseReader");
        readerThread.start();

        // 启动错误流读取线程
        Thread errorThread = new Thread(() -> readErrorStream(mcpProcess.getErrorStream()));
        errorThread.setDaemon(true);
        errorThread.setName("NotionMCP-ErrorReader");
        errorThread.start();

        // 发送初始化请求
        sendInitialize();

        connected = true;
        log.info("Notion MCP connected");
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
        clientInfo.put("name", "learning-agent-notion-client");
        clientInfo.put("version", "0.1.0");
        params.set("clientInfo", clientInfo);
        params.set("capabilities", objectMapper.createObjectNode());
        request.set("params", params);

        processWriter.write(objectMapper.writeValueAsString(request));
        processWriter.newLine();
        processWriter.flush();

        future.get(30, TimeUnit.SECONDS);
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
                log.debug("MCP raw response: {}", line);
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
                    log.warn("Failed to parse MCP response: {}", line);
                }
            }
            log.warn("MCP response reader exited - stream closed");
        } catch (IOException e) {
            if (!"Stream closed".equals(e.getMessage())) {
                log.error("Error reading MCP responses", e);
            }
        }
    }

    private void readErrorStream(InputStream errorStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.warn("MCP stderr: {}", line);
            }
        } catch (IOException e) {
            if (!"Stream closed".equals(e.getMessage())) {
                log.debug("Error stream reading ended", e);
            }
        }
    }

    private JsonNode parseResult(JsonNode result) {
        if (result == null) return null;

        if (result.has("content") && result.get("content").isArray()) {
            for (JsonNode item : result.get("content")) {
                if ("text".equals(item.get("type").asText()) && item.has("text")) {
                    try {
                        return objectMapper.readTree(item.get("text").asText());
                    } catch (JsonProcessingException e) {
                        return item;
                    }
                }
            }
        }
        return result;
    }

    private String extractResourceIdentifier(JsonNode result) {
        if (result == null) return null;

        JsonNode parsed = parseResult(result);
        if (parsed != null && parsed.has("id")) {
            return parsed.get("id").asText();
        }

        if (result.has("content") && result.get("content").isArray()) {
            for (JsonNode item : result.get("content")) {
                if ("resource_link".equals(item.get("type").asText()) && item.has("uri")) {
                    return item.get("uri").asText();
                }
            }
        }

        return null;
    }

    private String extractUrl(JsonNode result) {
        JsonNode parsed = parseResult(result);
        if (parsed != null && parsed.has("url")) {
            return parsed.get("url").asText();
        }
        return null;
    }
}
