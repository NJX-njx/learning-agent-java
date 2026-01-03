package com.learning.agent.workflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.client.NotionTools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 模式执行器
 * 用于不支持标准 Function Calling 的 LLM
 * <p>
 * ReAct = Reasoning + Acting
 * LLM 生成 JSON 格式的操作指令，系统解析并执行
 */
@Slf4j
@Component
public class ReactExecutor {

    private final ChatLanguageModel chatModel;
    private final NotionTools notionTools;
    private final ObjectMapper objectMapper;

    private static final int MAX_ITERATIONS = 5;

    /**
     * ReAct 执行结果
     */
    public record ReactResult(String finalAnswer, List<CreatedPageInfo> createdPages) {
    }

    /**
     * 创建的页面信息
     */
    public record CreatedPageInfo(String id, String url) {
    }

    private static final String REACT_SYSTEM_PROMPT = """
            你是一个智能助手，需要通过工具调用来完成任务。
            
            你可以使用以下工具：
            1. notionSearch(query: string) - 搜索 Notion 页面
            2. notionCreatePage(parentPageId: string, title: string, content: string) - 创建 Notion 页面
            3. notionAppendContent(pageId: string, content: string) - 追加内容到页面
            
            ### 重要规则
            1. 你必须使用 JSON 格式输出操作指令
            2. 每次只能输出一个操作
            3. 等待操作结果后再决定下一步
            
            ### 输出格式
            使用以下格式输出你的思考和行动：
            
            **Thought**: [你的思考过程]
            **Action**: [操作的 JSON 格式]
            
            JSON 格式示例：
            ```json
            {
              "tool": "notionSearch",
              "parameters": {
                "query": "sophie"
              }
            }
            ```
            
            或者当任务完成时：
            **Thought**: [总结]
            **Final Answer**: [最终结果描述]
            
            ### 执行流程示例
            任务：在 sophie 页面下创建名为"测试"的笔记
            
            第1轮：
            **Thought**: 需要先搜索 sophie 页面获取 ID
            **Action**:
            ```json
            {
              "tool": "notionSearch",
              "parameters": {
                "query": "sophie"
              }
            }
            ```
            
            观察: {"found": true, "id": "xxx", "title": "sophie"}
            
            第2轮：
            **Thought**: 已获得父页面 ID，现在创建新页面
            **Action**:
            ```json
            {
              "tool": "notionCreatePage",
              "parameters": {
                "parentPageId": "xxx",
                "title": "测试",
                "content": "这是测试内容"
              }
            }
            ```
            
            观察: {"id": "yyy", "url": "https://..."}
            
            第3轮：
            **Thought**: 页面创建成功
            **Final Answer**: 已成功创建页面"测试"，URL: https://...
            """;

    public ReactExecutor(
            @Qualifier("executionChatModel") ChatLanguageModel chatModel,
            NotionTools notionTools,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.notionTools = notionTools;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用 ReAct 模式执行任务
     */
    public ReactResult execute(String task) {
        log.info("🔄 Starting ReAct execution for task");

        List<String> conversationHistory = new ArrayList<>();
        conversationHistory.add("Task: " + task);

        // 追踪创建的页面
        List<CreatedPageInfo> createdPages = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            log.info("🔄 ReAct Iteration {}/{}", iteration + 1, MAX_ITERATIONS);

            // 构建提示
            String prompt = buildPrompt(conversationHistory);

            // 调用 LLM
            Response<AiMessage> response = chatModel.generate(
                    SystemMessage.from(REACT_SYSTEM_PROMPT),
                    UserMessage.from(prompt));

            String llmOutput = response.content().text();
            log.debug("LLM Output:\n{}", llmOutput);

            // 检查是否完成
            if (llmOutput.contains("**Final Answer**")) {
                String finalAnswer = extractFinalAnswer(llmOutput);
                log.info("✅ ReAct execution completed");
                log.info("📄 Created {} page(s)", createdPages.size());
                return new ReactResult(finalAnswer, createdPages);
            }

            // 提取并执行操作
            Optional<ToolCall> toolCall = extractToolCall(llmOutput);
            if (toolCall.isPresent()) {
                ToolCall call = toolCall.get();
                log.info("🛠️ Executing tool: {}", call.tool);

                String observation = executeToolCall(call);
                log.info("👁️ Observation: {}", observation);

                // 捕获页面创建信息
                if ("notionCreatePage".equals(call.tool)) {
                    extractPageInfoFromObservation(observation, createdPages);
                }

                conversationHistory.add(llmOutput);
                conversationHistory.add("Observation: " + observation);
            } else {
                log.warn("⚠️ No valid tool call found in LLM output");
                conversationHistory.add(llmOutput);
                conversationHistory.add("Observation: 未能识别有效的工具调用，请使用正确的 JSON 格式");
            }
        }

        log.warn("⚠️ Max iterations reached without completion");
        return new ReactResult("任务执行未完成：达到最大迭代次数", createdPages);
    }

    /**
     * 从工具执行观察结果中提取页面信息
     */
    private void extractPageInfoFromObservation(String observation, List<CreatedPageInfo> pages) {
        try {
            // 解析 JSON 观察结果
            if (observation.contains("\"id\"") && observation.contains("\"url\"")) {
                Map<String, Object> result = objectMapper.readValue(observation, new TypeReference<>() {
                });
                String id = (String) result.get("id");
                String url = (String) result.get("url");
                if (id != null) {
                    pages.add(new CreatedPageInfo(id, url));
                    log.debug("📄 Captured created page: {} -> {}", id, url);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract page info from observation: {}", e.getMessage());
        }
    }

    private String buildPrompt(List<String> history) {
        StringBuilder sb = new StringBuilder();
        for (String entry : history) {
            sb.append(entry).append("\n\n");
        }
        sb.append("请继续执行任务（输出 Thought 和 Action 或 Final Answer）：");
        return sb.toString();
    }

    private Optional<ToolCall> extractToolCall(String text) {
        // 提取 JSON 代码块
        Pattern jsonPattern = Pattern.compile("```json\\s*\\n(.*?)\\n```", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(text);

        if (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {
                });
                String tool = (String) parsed.get("tool");
                @SuppressWarnings("unchecked")
                Map<String, Object> parameters = (Map<String, Object>) parsed.get("parameters");

                if (tool != null && parameters != null) {
                    return Optional.of(new ToolCall(tool, parameters));
                }
            } catch (Exception e) {
                log.error("Failed to parse tool call JSON: {}", json, e);
            }
        }

        return Optional.empty();
    }

    private String executeToolCall(ToolCall call) {
        try {
            return notionTools.executeTool(call.tool, call.parameters);
        } catch (Exception e) {
            log.error("Tool execution failed", e);
            return String.format("{\"error\": \"%s\"}", e.getMessage());
        }
    }

    private String extractFinalAnswer(String text) {
        Pattern pattern = Pattern.compile("\\*\\*Final Answer\\*\\*:?\\s*(.+)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return text;
    }

    private record ToolCall(String tool, Map<String, Object> parameters) {
    }
}
