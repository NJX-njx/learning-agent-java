package com.learning.agent.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.NotionWritePayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Notion 工具类
 * 提供给 Spring AI Agent 使用的 Notion 操作工具
 */
@Slf4j
@Component
public class NotionTools {

    private final NotionClient notionClient;
    private final ObjectMapper objectMapper;

    public NotionTools(NotionClient notionClient, ObjectMapper objectMapper) {
        this.notionClient = notionClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 根据标题搜索 Notion 页面
     */
    @Description("根据标题搜索 Notion 页面。在读取或编辑之前使用此工具查找页面 ID。参数: query - 要在页面标题中搜索的文本")
    public String notionSearch(String query) {
        log.info("🔧 [TOOL CALL] notionSearch: query={}", query);
        Optional<NotionClient.SearchResult> result = notionClient.searchPage(query);
        if (result.isPresent()) {
            return toJson(Map.of(
                    "found", true,
                    "title", result.get().title(),
                    "id", result.get().id()
            ));
        }
        return toJson(Map.of("found", false));
    }

    /**
     * 在 Notion 中创建一个新页面
     */
    @Description("在 Notion 中创建一个新页面。参数: parentPageId - 父页面的 ID, title - 新页面的标题, content - 页面的 Markdown 内容")
    public String notionCreatePage(String parentPageId, String title, String content) {
        log.info("🔧 [TOOL CALL] notionCreatePage: parentPageId={}, title={}", parentPageId, title);

        NotionWritePayload payload = NotionWritePayload.builder()
                .parentPageId(parentPageId)
                .title(title)
                .markdownContent(content)
                .properties(new HashMap<>())
                .build();

        NotionCreatedPage result = notionClient.createPage(payload);
        return toJson(Map.of("id", result.getId(), "url", result.getUrl() != null ? result.getUrl() : ""));
    }

    /**
     * 将内容追加到现有 Notion 页面的末尾
     */
    @Description("将内容追加到现有 Notion 页面的末尾。参数: pageId - 要追加内容的页面 ID, content - 要追加的 Markdown 内容")
    public String notionAppendContent(String pageId, String content) {
        log.info("🔧 [TOOL CALL] notionAppendContent: pageId={}", pageId);

        List<Map<String, Object>> children = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            Map<String, Object> block = new HashMap<>();
            block.put("object", "block");
            block.put("type", "paragraph");

            Map<String, Object> paragraph = new HashMap<>();
            List<Map<String, Object>> richText = new ArrayList<>();
            Map<String, Object> textObj = new HashMap<>();
            textObj.put("type", "text");
            textObj.put("text", Map.of("content", line));
            richText.add(textObj);
            paragraph.put("rich_text", richText);
            block.put("paragraph", paragraph);

            children.add(block);
        }

        notionClient.appendBlockChildren(pageId, children);
        return toJson(Map.of("success", true));
    }

    /**
     * 更新 Notion 页面属性
     */
    @Description("更新 Notion 页面属性（如归档页面）。参数: pageId - 要更新的页面 ID, propertiesJson - JSON 格式的属性对象")
    public String notionUpdatePage(String pageId, String propertiesJson) {
        log.info("🔧 [TOOL CALL] notionUpdatePage: pageId={}", pageId);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = objectMapper.readValue(propertiesJson, Map.class);
            notionClient.updatePage(pageId, properties);
            return toJson(Map.of("success", true));
        } catch (JsonProcessingException e) {
            log.error("Failed to parse properties JSON: {}", propertiesJson, e);
            return toJson(Map.of("success", false, "error", "Invalid JSON format"));
        }
    }

    /**
     * 通过 ID 获取用户
     */
    @Description("通过 ID 获取用户。参数: userId - 用户的 ID")
    public String notionGetUser(String userId) {
        Object result = notionClient.getUser(userId);
        return toJson(result);
    }

    /**
     * 列出所有用户
     */
    @Description("列出所有用户。参数: pageSize - 要返回的用户数量, startCursor - 分页游标")
    public String notionListUsers(Integer pageSize, String startCursor) {
        Object result = notionClient.listUsers(pageSize, startCursor);
        return toJson(result);
    }

    /**
     * 获取机器人用户自身信息
     */
    @Description("获取机器人用户自身信息。")
    public String notionGetSelf() {
        Object result = notionClient.getSelf();
        return toJson(result);
    }

    /**
     * 使用过滤和排序查询数据库
     */
    @Description("使用过滤和排序查询数据库。参数: databaseId - 数据库的 ID, filter - 过滤对象的 JSON 字符串, sorts - 排序对象的 JSON 字符串, pageSize - 要返回的结果数量, startCursor - 分页游标")
    public String notionQueryDatabase(String databaseId, String filter, String sorts, Integer pageSize, String startCursor) {
        Object result = notionClient.queryDatabase(
                databaseId,
                parseJson(filter),
                parseJson(sorts),
                pageSize,
                startCursor
        );
        return toJson(result);
    }

    /**
     * 在工作区中搜索页面或数据库
     */
    @Description("在工作区中搜索页面或数据库。参数: query - 搜索查询, filter - 过滤条件的 JSON 字符串, sort - 排序条件的 JSON 字符串, pageSize - 要返回的结果数量, startCursor - 分页游标")
    public String notionSearchAll(String query, String filter, String sort, Integer pageSize, String startCursor) {
        Object result = notionClient.search(
                query,
                parseJson(filter),
                parseJson(sort),
                pageSize,
                startCursor
        );
        return toJson(result);
    }

    /**
     * 获取块或页面的子块
     */
    @Description("获取块或页面的子块。参数: blockId - 块或页面的 ID, pageSize - 要返回的结果数量, startCursor - 分页游标")
    public String notionGetBlockChildren(String blockId, Integer pageSize, String startCursor) {
        Object result = notionClient.getBlockChildren(blockId, pageSize, startCursor);
        return toJson(result);
    }

    /**
     * 获取特定块
     */
    @Description("获取特定块。参数: blockId - 块的 ID")
    public String notionRetrieveBlock(String blockId) {
        Object result = notionClient.retrieveBlock(blockId);
        return toJson(result);
    }

    /**
     * 通过 ID 获取页面
     */
    @Description("通过 ID 获取页面。参数: pageId - 页面的 ID")
    public String notionRetrievePage(String pageId) {
        Object result = notionClient.retrievePage(pageId);
        return toJson(result);
    }

    /**
     * 通过 ID 获取数据库
     */
    @Description("通过 ID 获取数据库。参数: databaseId - 数据库的 ID")
    public String notionRetrieveDatabase(String databaseId) {
        Object result = notionClient.retrieveDatabase(databaseId);
        return toJson(result);
    }

    /**
     * 通过工具名称执行工具
     */
    @SuppressWarnings("unchecked")
    public String executeTool(String toolName, Map<String, Object> args) {
        return switch (toolName) {
            case "notion_search", "notionSearch" -> notionSearch((String) args.get("query"));
            case "notion_create_page", "notionCreatePage" -> notionCreatePage(
                    (String) args.get("parentPageId"),
                    (String) args.get("title"),
                    (String) args.get("content")
            );
            case "notion_append_content", "notionAppendContent" -> notionAppendContent(
                    (String) args.get("pageId"),
                    (String) args.get("content")
            );
            case "notion_update_page", "notionUpdatePage" -> notionUpdatePage(
                    (String) args.get("pageId"),
                    (String) args.get("propertiesJson")
            );
            case "notion_get_user", "notionGetUser" -> notionGetUser((String) args.get("userId"));
            case "notion_list_users", "notionListUsers" -> notionListUsers(
                    args.get("pageSize") instanceof Number n ? n.intValue() : null,
                    (String) args.get("startCursor")
            );
            case "notion_get_self", "notionGetSelf" -> notionGetSelf();
            case "notion_query_database", "notionQueryDatabase" -> notionQueryDatabase(
                    (String) args.get("databaseId"),
                    (String) args.get("filter"),
                    (String) args.get("sorts"),
                    args.get("pageSize") instanceof Number n ? n.intValue() : null,
                    (String) args.get("startCursor")
            );
            case "notion_search_all", "notionSearchAll" -> notionSearchAll(
                    (String) args.get("query"),
                    (String) args.get("filter"),
                    (String) args.get("sort"),
                    args.get("pageSize") instanceof Number n ? n.intValue() : null,
                    (String) args.get("startCursor")
            );
            case "notion_get_block_children", "notionGetBlockChildren" -> notionGetBlockChildren(
                    (String) args.get("blockId"),
                    args.get("pageSize") instanceof Number n ? n.intValue() : null,
                    (String) args.get("startCursor")
            );
            case "notion_retrieve_block", "notionRetrieveBlock" -> notionRetrieveBlock(
                    (String) args.get("blockId")
            );
            case "notion_retrieve_page", "notionRetrievePage" -> notionRetrievePage(
                    (String) args.get("pageId")
            );
            case "notion_retrieve_database", "notionRetrieveDatabase" -> notionRetrieveDatabase(
                    (String) args.get("databaseId")
            );
            default -> "Unknown tool: " + toolName;
        };
    }

    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
