package com.learning.agent.integration;

import com.learning.agent.config.client.ToolFunctionsConfig;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 工具调用测试
 * 测试 LangChain4j 与文心一言的 function calling 集成
 */
@Slf4j
@SpringBootTest
public class ToolCallingTest {

    @Autowired
    @Qualifier("executionChatModel")
    private ChatLanguageModel chatModel;

    @Autowired
    private ToolFunctionsConfig.NotionToolService notionToolService;

    interface ToolTestAgent {
        @dev.langchain4j.service.SystemMessage("""
                你是一个测试助手。你有权限调用 Notion API 工具。
                
                当用户要求搜索页面时，你必须调用 notionSearch 工具。
                当用户要求创建页面时，你必须调用 notionCreatePage 工具。
                
                不要在文本中描述你要做什么，直接调用工具即可。
                """)
        String executeCommand(String command);
    }

    @Test
    public void testSearchToolCalling() {
        log.info("=== 测试搜索工具调用 ===");

        ToolTestAgent agent = AiServices.builder(ToolTestAgent.class)
                .chatLanguageModel(chatModel)
                .tools(notionToolService)
                .build();

        String result = agent.executeCommand("搜索名为 'sophie' 的 Notion 页面");

        log.info("Agent 响应: {}", result);
    }

    @Test
    public void testCreatePageToolCalling() {
        log.info("=== 测试创建页面工具调用 ===");

        ToolTestAgent agent = AiServices.builder(ToolTestAgent.class)
                .chatLanguageModel(chatModel)
                .tools(notionToolService)
                .build();

        // 注意：这需要有效的 parentPageId
        String result = agent.executeCommand(
                "首先搜索 'sophie' 页面获取其 ID，然后在该页面下创建一个新页面，标题是 '测试页面'，内容是 '这是测试内容'"
        );

        log.info("Agent 响应: {}", result);
    }

    @Test
    public void testDirectToolCall() {
        log.info("=== 测试直接工具调用 ===");

        // 直接调用工具（不通过 LLM）
        String searchResult = notionToolService.notionSearch("sophie");
        log.info("直接搜索结果: {}", searchResult);
    }
}
