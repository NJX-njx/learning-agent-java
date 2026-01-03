package com.learning.agent.config.client;

import com.learning.agent.client.NotionTools;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具函数配置类
 * 将 NotionTools 中的方法注册为 LangChain4j 可调用的函数
 */
@Configuration
public class ToolFunctionsConfig {

    /**
     * 注册 NotionToolService 作为工具提供者
     */
    @Bean
    public NotionToolService notionToolService(NotionTools notionTools) {
        return new NotionToolService(notionTools);
    }

    /**
     * Notion 工具服务类
     * 使用 LangChain4j 的 @Tool 注解定义工具
     */
    public static class NotionToolService {

        private final NotionTools notionTools;

        public NotionToolService(NotionTools notionTools) {
            this.notionTools = notionTools;
        }

        @Tool("根据标题搜索 Notion 页面。在读取或编辑之前使用此工具查找页面 ID。")
        public String notionSearch(@P("搜索查询字符串") String query) {
            return notionTools.notionSearch(query);
        }

        @Tool("在 Notion 中创建一个新页面。需要父页面 ID、标题和 Markdown 内容。")
        public String notionCreatePage(
                @P("父页面 ID") String parentPageId,
                @P("新页面标题") String title,
                @P("页面内容 (Markdown 格式)") String content) {
            return notionTools.notionCreatePage(parentPageId, title, content);
        }

        @Tool("将内容追加到现有 Notion 页面的末尾。")
        public String notionAppendContent(
                @P("页面 ID") String pageId,
                @P("要追加的内容 (Markdown 格式)") String content) {
            return notionTools.notionAppendContent(pageId, content);
        }

        @Tool("获取机器人用户自身信息。")
        public String notionGetSelf() {
            return notionTools.notionGetSelf();
        }

        @Tool("在工作区中搜索页面或数据库。")
        public String notionSearchAll(
                @P("搜索查询") String query,
                @P("过滤器") String filter,
                @P("排序") String sort,
                @P("页面大小") Integer pageSize,
                @P("起始游标") String startCursor) {
            return notionTools.notionSearchAll(query, filter, sort, pageSize, startCursor);
        }

        @Tool("通过 ID 获取页面。")
        public String notionRetrievePage(@P("页面 ID") String pageId) {
            return notionTools.notionRetrievePage(pageId);
        }
    }
}
