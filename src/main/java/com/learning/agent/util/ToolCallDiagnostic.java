package com.learning.agent.util;

import com.learning.agent.config.client.ToolFunctionsConfig;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具调用诊断运行器
 * 在应用启动时测试工具调用是否正常工作
 */
@Slf4j
@Component
public class ToolCallDiagnostic implements CommandLineRunner {

    @Autowired
    @Qualifier("executionChatModel")
    private ChatLanguageModel chatModel;

    @Autowired
    private ToolFunctionsConfig.NotionToolService notionToolService;

    @Override
    public void run(String... args) throws Exception {
        // 如果有命令行参数 --diagnose-tools，则运行诊断
        for (String arg : args) {
            if ("--diagnose-tools".equals(arg)) {
                log.info("=== 工具调用诊断开始 ===");
                diagnoseFunctionCalling();
                log.info("=== 工具调用诊断结束 ===");
                break;
            }
        }
    }

    private void diagnoseFunctionCalling() {
        log.info("1. 测试直接工具调用...");
        testDirectToolCall();

        log.info("\n2. 检查工具定义...");
        inspectToolDefinitions();

        log.info("\n3. 测试 LLM 基础响应（无工具）...");
        testBasicLLMResponse();

        log.info("\n4. 测试 LLM 工具调用意图识别...");
        testToolCallIntent();
    }

    private void testDirectToolCall() {
        try {
            String result = notionToolService.notionSearch("sophie");
            log.info("✅ 直接工具调用成功: {}", result);
        } catch (Exception e) {
            log.error("❌ 直接工具调用失败", e);
        }
    }

    private void inspectToolDefinitions() {
        try {
            Method[] methods = ToolFunctionsConfig.NotionToolService.class.getDeclaredMethods();
            log.info("NotionToolService 可用方法数量: {}", methods.length);
            for (Method method : methods) {
                log.info("  - {}: 参数数量={}", method.getName(), method.getParameterCount());
            }
        } catch (Exception e) {
            log.error("❌ 工具定义检查失败", e);
        }
    }

    private void testBasicLLMResponse() {
        try {
            Response<AiMessage> response = chatModel.generate(
                    SystemMessage.from("你是一个助手。"),
                    UserMessage.from("请用一句话介绍你自己。")
            );
            log.info("✅ LLM 基础响应: {}", response.content().text());
        } catch (Exception e) {
            log.error("❌ LLM 基础响应失败", e);
        }
    }

    private void testToolCallIntent() {
        try {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from("""
                    你是一个测试助手。你有权限调用 Notion API 工具。
                    当需要搜索页面时，调用 notionSearch 工具。
                    直接调用工具，不要描述你要做什么。
                    """));
            messages.add(UserMessage.from("搜索名为 'test' 的页面"));

            Response<AiMessage> response = chatModel.generate(messages);
            AiMessage aiMessage = response.content();

            log.info("LLM 响应文本: {}", aiMessage.text());

            if (aiMessage.hasToolExecutionRequests()) {
                log.info("✅ LLM 生成了工具调用请求！");
                for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {
                    log.info("  工具名称: {}", request.name());
                    log.info("  工具参数: {}", request.arguments());
                }
            } else {
                log.warn("⚠️ LLM 未生成工具调用请求，可能模型不支持或提示词需要优化");
            }
        } catch (Exception e) {
            log.error("❌ 工具调用意图测试失败", e);
        }
    }
}
