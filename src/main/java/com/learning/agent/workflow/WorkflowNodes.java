package com.learning.agent.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.client.PaddleOcrClient;
import com.learning.agent.config.client.ToolFunctionsConfig;
import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.OcrStructuredResult;
import com.learning.agent.model.*;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流节点实现
 * 包含 OCR、规划和执行三个核心节点
 * 使用 LangChain4j 进行 LLM 调用和工具执行
 */
@Slf4j
@Component
public class WorkflowNodes {

    private final PaddleOcrClient ocrClient;
    private final ChatLanguageModel planningChatModel;
    private final ChatLanguageModel executionChatModel;
    private final ToolFunctionsConfig.NotionToolService notionToolService;
    private final ReactExecutor reactExecutor;
    private final ObjectMapper objectMapper;

    // 是否使用 ReAct 模式（文心一言不支持标准 function calling）
    private final boolean useReactMode;

    // System prompts
    private static final String PLANNING_SYSTEM_PROMPT = """
            你是一名规划师。你的任务是根据可选的用户请求、OCR 识别的学习材料，以及学习者画像，制定一份详尽、可执行、结构化的智能体执行的任务清单。
            
            你接收的信息可能包括：
            - `用户请求`：用户希望达成的学习目标或解决的问题。
            - `OCR内容`：从图片中提取的原始文本（ocr-plain）和结构化内容（ocr-markdown），包括文本、表格、公式等信息。
            - `学习者画像`：学习者的ID、当前水平、学习目标和学习偏好。
            
            请严格按照以下步骤进行分析与任务拆解，并输出一个结构化的 JSON 任务数组。
            
            ## OCR 内容解读
            - `<ocr-plain>`: 图片中识别出的纯文本内容（如果为空或提示"未上传图片"，说明没有图片输入）
            - `<ocr-markdown>`: 结构化的 Markdown 格式内容，包含标题、列表、表格等（可能为空）
            - **重要**: 请优先使用 ocr-markdown 中的结构化内容，如果为空则使用 ocr-plain
            
            ## 输出格式规范
            请严格按照以下格式输出 JSON 数组，每个任务为一个对象：
            {
                "taskId": "T1",
                "type": "execution",
                "description": "详细描述，包含明确步骤和工具调用指令（如果有 OCR 内容，请明确引用）",
                "priority": 5,
                "dueDate": "2025-11-25T10:00:00Z",
                "estimatedDuration": "30min"
            }
            
            ## 关键规则
            1. 若 OCR 内容不为空，必须基于 OCR 内容和用户请求生成任务，不要忽略图片内容
            2. 若 OCR 内容为空，完全基于用户请求生成任务
            3. 简单请求生成单个 execution 类型任务
            4. 只输出 JSON 数组，不要添加 Markdown 代码块标记
            """;

    private static final String EXECUTION_SYSTEM_PROMPT = """
            你是一名专注 K12/高校学习的教师以及笔记爱好者，拥有完整的 Notion 操作权限。
            你的核心职责是：执行规划智能体制定的任务，产出高质量的内容，并根据需要调用工具将结果持久化到 Notion 中。
            
            ### ⚠️ 关键指令：关于工具调用 ⚠️
            你配备了 Function Calling (工具调用) 能力。当需要操作 Notion 时（如搜索、创建页面），你必须**直接调用函数**。
            
            ❌ **严禁**在回复文本中输出 JSON 代码块来模拟工具调用。
            ❌ **严禁**在回复文本中描述你要做什么（例如："我将调用搜索工具..."）。
            ✅ **必须**直接发起 Function Call 请求。
            ✅ **必须**先调用 notionSearch 搜索 'sophie' 获取父页面 ID
            ✅ **然后**使用获取的 ID 调用 notionCreatePage 创建页面
            
            ### 核心原则
            1. **工具优先**：如果任务目标是"写入 Notion"或"创建笔记"，必须直接调用工具，不要在对话中输出长篇内容。
            2. **引用证据**：任何结论都必须引用 OCR 内容或 Notion 数据字段。
            3. **主动搜索**：在创建新页面前，先调用搜索工具确认是否已存在相关页面。
            4. **内容纯净性**：生成 Notion 页面内容时，不要包含任务元数据（如 Priority, Type 等）。
            
            ### 工具使用指南
            - **创建新笔记**：使用 notionCreatePage，需要 parentPageId
            - **追加内容**：使用 notionAppendContent，需要 pageId
            - **查询信息**：使用 notionSearch 或 notionSearchAll
            
            ### 执行流程
            1. 分析当前任务描述
            2. 判断是否需要操作 Notion
            3. 如需操作，先检查必要参数（如 pageId）
            4. 调用写入/修改工具 (Function Call)
            5. 确认工具执行成功后，汇报结果
            """;

    public WorkflowNodes(
            PaddleOcrClient ocrClient,
            @Qualifier("planningChatModel") ChatLanguageModel planningChatModel,
            @Qualifier("executionChatModel") ChatLanguageModel executionChatModel,
            ToolFunctionsConfig.NotionToolService notionToolService,
            ReactExecutor reactExecutor,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${agent.execution.use-react-mode:true}") boolean useReactMode) {
        this.ocrClient = ocrClient;
        this.planningChatModel = planningChatModel;
        this.executionChatModel = executionChatModel;
        this.notionToolService = notionToolService;
        this.reactExecutor = reactExecutor;
        this.objectMapper = objectMapper;
        this.useReactMode = useReactMode;
    }

    /**
     * OCR 节点 - 处理图片识别
     */
    public WorkflowNode createOcrNode() {
        return state -> {
            log.info("--- Node: OCR ---");

            if (state.getOcrResult() != null) {
                log.info("OCR result already exists, skipping.");
                return state;
            }

            if (state.getImagePath() == null || state.getImagePath().isEmpty()) {
                log.info("No image path provided, skipping OCR.");
                state.setOcrResult(OcrStructuredResult.empty());
                return state;
            }

            try {
                log.info("Running OCR on: {}", state.getImagePath());
                OcrStructuredResult result = ocrClient.runStructuredOcr(state.getImagePath());

                if (result.isSuccess()) {
                    log.info("OCR completed successfully.");
                    state.setOcrResult(result);
                } else {
                    log.error("OCR failed: {}", result.getMarkdownText());
                    state.setOcrResult(result);
                    throw new RuntimeException("OCR failed: " + result.getMarkdownText());
                }
            } catch (Exception e) {
                log.error("OCR failed: {}", e.getMessage(), e);
                state.setOcrResult(OcrStructuredResult.failure(state.getImagePath(), e.getMessage()));
                throw e;
            }

            return state;
        };
    }

    /**
     * 规划节点 - 生成任务列表
     */
    public WorkflowNode createPlanningNode() {
        return state -> {
            log.info("--- Node: Planning ---");

            boolean hasOcrContent = state.getOcrResult() != null
                    && state.getOcrResult().getPlainText() != null
                    && !state.getOcrResult().getPlainText().trim().isEmpty();

            String planningInput = buildPlanningInput(state, hasOcrContent);

            try {
                // 使用 LangChain4j 调用模型
                Response<AiMessage> response = planningChatModel.generate(
                        SystemMessage.from(PLANNING_SYSTEM_PROMPT),
                        UserMessage.from(planningInput));

                String content = response.content().text();
                log.debug("Planning response: {}", content);

                List<LearningTask> tasks = parseTasksFromJson(content);
                log.info("Generated Plan: {}", objectMapper.writeValueAsString(tasks));

                state.setTasks(new ArrayList<>(tasks));
            } catch (Exception e) {
                log.error("Planning failed: {}", e.getMessage(), e);
                throw new IllegalStateException("规划执行失败: " + e.getMessage(), e);
            }

            return state;
        };
    }

    /**
     * 执行节点 - 执行单个任务
     */
    public WorkflowNode createExecutionNode() {
        return state -> {
            log.info("--- Node: Execution (Task {}) ---", state.getCurrentTaskIndex());

            LearningTask task = state.getCurrentTask();
            if (task == null) {
                throw new RuntimeException("No task found for current index");
            }

            String userPrompt = buildExecutionPrompt(state, task);

            List<String> newCreatedPageIds = new ArrayList<>();
            List<NotionCreatedPage> newCreatedPages = new ArrayList<>();

            String finalContent;

            try {
                log.debug("=== Execution Prompt ===\n{}", userPrompt);

                if (useReactMode) {
                    // 使用 ReAct 模式（适用于不支持标准 function calling 的模型）
                    log.info("Using ReAct mode for execution");
                    ReactExecutor.ReactResult result = reactExecutor.execute(userPrompt);
                    finalContent = result.finalAnswer();

                    // 收集页面信息
                    for (ReactExecutor.CreatedPageInfo pageInfo : result.createdPages()) {
                        newCreatedPages.add(NotionCreatedPage.builder()
                                .id(pageInfo.id())
                                .url(pageInfo.url())
                                .build());
                    }
                } else {
                    // 使用标准 Function Calling（适用于 OpenAI、Claude 等）
                    log.info("Using standard function calling mode");
                    NotionExecutor executor = AiServices.builder(NotionExecutor.class)
                            .chatLanguageModel(executionChatModel)
                            .tools(notionToolService)
                            .build();

                    String result = executor.executeTask(userPrompt);
                    finalContent = result != null ? result : "任务执行完成";
                }

                log.info("LLM Response: {}", finalContent.length() > 500
                        ? finalContent.substring(0, 500) + "..."
                        : finalContent);
                log.info("Task execution completed.");

                // 从输出中提取页面信息
                extractPageInfoFromString(finalContent, newCreatedPageIds, newCreatedPages);

            } catch (Exception e) {
                log.error("Task execution failed: {}", e.getMessage(), e);
                finalContent = "任务执行失败: " + e.getMessage();
            }

            // 添加页面链接到输出
            if (!newCreatedPages.isEmpty()) {
                StringBuilder links = new StringBuilder("\n\n> **相关链接**：");
                for (NotionCreatedPage page : newCreatedPages) {
                    if (page.getUrl() != null) {
                        links.append(String.format("[📄 查看 Notion 页面](%s)  ", page.getUrl()));
                    } else {
                        links.append(String.format("页面 ID: %s  ", page.getId()));
                    }
                }
                finalContent += links.toString();
            }

            state.addGeneratedContent(finalContent);
            for (NotionCreatedPage page : newCreatedPages) {
                state.addCreatedPage(page.getId(), page.getUrl());
            }
            state.moveToNextTask();

            return state;
        };
    }

    // --- Helper Methods ---

    private String buildPlanningInput(AgentState state, boolean hasOcrContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前上下文信息：\n\n");

        sb.append("<learner>\n");
        sb.append("ID: ").append(state.getLearnerProfile().getLearnerId()).append("\n");
        sb.append("水平: ").append(state.getLearnerProfile().getCompetencyLevel()).append("\n");
        sb.append("目标: ").append(state.getLearnerProfile().getLearningGoal()).append("\n");
        sb.append("偏好: ").append(state.getLearnerProfile().getPreferredStyle()).append("\n");
        sb.append("</learner>\n\n");

        if (state.getUserQuery() != null && !state.getUserQuery().isEmpty()) {
            sb.append("<user-query>\n").append(state.getUserQuery()).append("\n</user-query>\n\n");
        } else {
            sb.append("<user-query>（用户未输入文字）</user-query>\n\n");
        }

        if (hasOcrContent) {
            sb.append("<ocr-plain>\n").append(state.getOcrResult().getPlainText()).append("\n</ocr-plain>\n");
            sb.append("<ocr-markdown>\n").append(state.getOcrResult().getMarkdownText()).append("\n</ocr-markdown>\n");
        } else {
            sb.append("<ocr-plain>（本次对话未上传图片，或图片中无文字）</ocr-plain>\n");
            sb.append("<ocr-markdown></ocr-markdown>\n");
        }

        return sb.toString();
    }

    private String buildExecutionPrompt(AgentState state, LearningTask task) {
        StringBuilder sb = new StringBuilder();

        sb.append("当前任务:\n");
        sb.append("<task>\n");
        sb.append("类型: ").append(task.getType().getValue()).append("\n");
        sb.append("描述: ").append(task.getDescription()).append("\n");
        sb.append("优先级: ").append(task.getPriority()).append("\n");
        sb.append("截止: ").append(task.getDueDate() != null ? task.getDueDate() : "未设定").append("\n");
        sb.append("</task>\n\n");

        // 之前的执行结果
        if (!state.getGeneratedContents().isEmpty()) {
            sb.append("之前的执行结果:\n");
            for (int i = 0; i < state.getGeneratedContents().size(); i++) {
                String content = state.getGeneratedContents().get(i);
                String preview = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                sb.append(String.format("[Task %d Result]: %s\n---\n", i + 1, preview));
            }
            sb.append("\n");
        }

        sb.append("上下文信息:\n");
        sb.append("<learner>\n");
        sb.append("ID: ").append(state.getLearnerProfile().getLearnerId()).append("\n");
        sb.append("水平: ").append(state.getLearnerProfile().getCompetencyLevel()).append("\n");
        sb.append("目标: ").append(state.getLearnerProfile().getLearningGoal()).append("\n");
        sb.append("偏好: ").append(state.getLearnerProfile().getPreferredStyle()).append("\n");
        sb.append("</learner>\n\n");

        sb.append("重要提示：\n");
        sb.append("1. **Mandatory Search**: 使用 notionSearch 工具搜索 'sophie'，获取父页面 ID\n");
        sb.append("2. **Mandatory Create**: 使用 notionCreatePage 工具创建页面（parentPageId 从步骤1获取）\n");
        sb.append("3. **No Simulation**: 严禁输出 JSON 模拟，必须真正调用工具\n");
        sb.append("4. **Action Required**: 本任务必须调用工具，不能仅返回文字说明\n\n");

        if (state.getUserQuery() != null && !state.getUserQuery().isEmpty()) {
            sb.append("<user-query>\n").append(state.getUserQuery()).append("\n</user-query>\n\n");
        }

        if (state.getOcrResult() != null) {
            sb.append("<ocr-plain>\n").append(state.getOcrResult().getPlainText()).append("\n</ocr-plain>\n");
            sb.append("<ocr-markdown>\n").append(state.getOcrResult().getMarkdownText()).append("\n</ocr-markdown>\n");
        }

        sb.append("\n请执行该任务。");

        return sb.toString();
    }

    private List<LearningTask> parseTasksFromJson(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Planning output is null or blank");
        }
        // 尝试从 JSON 内容中提取任务数组
        String cleanedJson = json.trim();

        // 如果被 markdown 代码块包裹，去除
        if (cleanedJson.startsWith("```json")) {
            cleanedJson = cleanedJson.substring(7);
        } else if (cleanedJson.startsWith("```")) {
            cleanedJson = cleanedJson.substring(3);
        }
        if (cleanedJson.endsWith("```")) {
            cleanedJson = cleanedJson.substring(0, cleanedJson.length() - 3);
        }
        cleanedJson = cleanedJson.trim();

        List<Map<String, Object>> rawTasks = objectMapper.readValue(cleanedJson,
                new TypeReference<>() {
                });

        List<LearningTask> tasks = new ArrayList<>();
        for (Map<String, Object> rawTask : rawTasks) {
            LearningTask task = LearningTask.builder()
                    .taskId((String) rawTask.get("taskId"))
                    .type(LearningTaskType.fromValue((String) rawTask.get("type")))
                    .description((String) rawTask.get("description"))
                    .priority(rawTask.get("priority") instanceof Number n ? n.intValue() : 3)
                    .dueDate((String) rawTask.get("dueDate"))
                    .estimatedDuration((String) rawTask.get("estimatedDuration"))
                    .build();
            tasks.add(task);
        }
        return tasks;
    }

    private void extractPageInfoFromString(String output, List<String> pageIds, List<NotionCreatedPage> pages) {
        // 尝试 JSON 解析
        try {
            if (output.contains("\"id\"") && output.contains("\"url\"")) {
                // 查找 JSON 对象
                Pattern jsonPattern = Pattern
                        .compile("\\{[^{}]*\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"url\"\\s*:\\s*\"([^\"]*)\"[^{}]*}");
                Matcher matcher = jsonPattern.matcher(output);
                while (matcher.find()) {
                    String id = matcher.group(1);
                    String url = matcher.group(2);
                    if (id != null && !id.isEmpty() && !pageIds.contains(id)) {
                        pageIds.add(id);
                        pages.add(NotionCreatedPage.builder().id(id).url(url.isEmpty() ? null : url).build());
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse page info from output: {}", e.getMessage());
        }

        // 回退到正则匹配
        Pattern idPattern = Pattern.compile("ID:\\s*([a-zA-Z0-9-]+)");
        Pattern urlPattern = Pattern.compile("URL:\\s*(https?://[^\\s,\"]+)");

        Matcher idMatcher = idPattern.matcher(output);
        Matcher urlMatcher = urlPattern.matcher(output);

        if (idMatcher.find()) {
            String id = idMatcher.group(1);
            String url = urlMatcher.find() ? urlMatcher.group(1) : null;
            if (!pageIds.contains(id)) {
                pageIds.add(id);
                pages.add(NotionCreatedPage.builder().id(id).url(url).build());
            }
        }
    }

    /**
     * AI Service interface for task execution with tool support
     */
    interface NotionExecutor {
        @dev.langchain4j.service.SystemMessage(EXECUTION_SYSTEM_PROMPT)
        String executeTask(String userPrompt);
    }
}
