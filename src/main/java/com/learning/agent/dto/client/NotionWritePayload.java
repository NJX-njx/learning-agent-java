package com.learning.agent.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Notion 写入载荷
 * 创建 Notion 页面所需的最小字段集合
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotionWritePayload {

    /**
     * 目标父页面 ID
     */
    private String parentPageId;

    /**
     * 新页面标题
     */
    private String title;

    /**
     * 页面主体 Markdown
     */
    private String markdownContent;

    /**
     * 需要同步的数据库属性
     */
    private Map<String, Object> properties;
}
