package com.learning.agent.client;

import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.NotionWritePayload;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Notion 客户端接口
 * 抽象了智能体需要的 Notion 能力
 */
public interface NotionClient {

    /**
     * 创建新的 Notion 页面
     *
     * @param payload 页面内容载荷
     * @return 新页面 ID 和 URL
     */
    NotionCreatedPage createPage(NotionWritePayload payload);

    /**
     * 更新 Notion 页面属性或内容
     *
     * @param pageId     目标页面 ID
     * @param properties 属性键值
     */
    void updatePage(String pageId, Map<String, Object> properties);

    /**
     * 创建评论用于提醒
     *
     * @param pageId      目标页面 ID
     * @param commentText 评论文本
     */
    void createComment(String pageId, String commentText);

    /**
     * 根据标题搜索页面
     *
     * @param query 搜索关键词
     * @return 搜索结果（可能为空）
     */
    Optional<SearchResult> searchPage(String query);

    // --- Expanded Capabilities ---

    Object getUser(String userId);

    Object listUsers(Integer pageSize, String startCursor);

    Object getSelf();

    Object queryDatabase(String databaseId, Object filter, Object sorts, Integer pageSize, String startCursor);

    Object search(String query, Object filter, Object sort, Integer pageSize, String startCursor);

    Object getBlockChildren(String blockId, Integer pageSize, String startCursor);

    Object appendBlockChildren(String blockId, List<Map<String, Object>> children);

    Object retrieveBlock(String blockId);

    Object updateBlock(String blockId, Map<String, Object> block);

    Object deleteBlock(String blockId);

    Object retrievePage(String pageId);

    Object createDatabase(Object parent, List<Object> title, Map<String, Object> properties);

    Object updateDatabase(String databaseId, Map<String, Object> properties);

    Object retrieveDatabase(String databaseId);

    Object retrievePageProperty(String pageId, String propertyId);

    Object retrieveComments(String blockId, Integer pageSize, String startCursor);

    /**
     * 搜索结果
     */
    record SearchResult(String id, String title) {
    }
}
