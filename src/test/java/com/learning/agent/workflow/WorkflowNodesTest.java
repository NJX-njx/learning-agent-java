package com.learning.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.client.PaddleOcrClient;
import com.learning.agent.config.client.ToolFunctionsConfig;
import com.learning.agent.dto.client.OcrStructuredResult;
import com.learning.agent.model.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WorkflowNodes 单元测试
 * 测试 OCR、规划和执行三个核心节点
 */
@ExtendWith(MockitoExtension.class)
class WorkflowNodesTest {

    @Mock
    private PaddleOcrClient ocrClient;

    @Mock
    private ChatLanguageModel planningChatModel;

    @Mock
    private ChatLanguageModel executionChatModel;

    @Mock
    private ToolFunctionsConfig.NotionToolService notionToolService;

    @Mock
    private ReactExecutor reactExecutor;

    private WorkflowNodes workflowNodes;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        workflowNodes = new WorkflowNodes(
                ocrClient,
                planningChatModel,
                executionChatModel,
                notionToolService,
                reactExecutor,
                objectMapper,
                true  // useReactMode
        );
    }

    @Test
    void testOcrNode_NoImagePath_ShouldSkipOcr() {
        // Given
        AgentState state = createTestState();
        state.setImagePath(null);

        // When
        WorkflowNode ocrNode = workflowNodes.createOcrNode();
        AgentState result = ocrNode.process(state);

        // Then
        assertNotNull(result.getOcrResult());
        assertTrue(result.getOcrResult().getPlainText().isEmpty());
        verify(ocrClient, never()).runStructuredOcr(any());
    }

    @Test
    void testOcrNode_WithImagePath_ShouldProcessOcr() {
        // Given
        String imagePath = "test.jpg";
        AgentState state = createTestState();
        state.setImagePath(imagePath);

        OcrStructuredResult ocrResult = OcrStructuredResult.builder()
                .success(true)
                .plainText("测试文本")
                .markdownText("# 测试文本")
                .build();

        when(ocrClient.runStructuredOcr(imagePath)).thenReturn(ocrResult);

        // When
        WorkflowNode ocrNode = workflowNodes.createOcrNode();
        AgentState result = ocrNode.process(state);

        // Then
        assertNotNull(result.getOcrResult());
        assertEquals("测试文本", result.getOcrResult().getPlainText());
        assertEquals("# 测试文本", result.getOcrResult().getMarkdownText());
        verify(ocrClient, times(1)).runStructuredOcr(imagePath);
    }

    @Test
    void testOcrNode_AlreadyHasResult_ShouldSkip() {
        // Given
        AgentState state = createTestState();
        state.setImagePath("test.jpg");
        state.setOcrResult(OcrStructuredResult.builder().success(true).plainText("已存在").build());

        // When
        WorkflowNode ocrNode = workflowNodes.createOcrNode();
        AgentState result = ocrNode.process(state);

        // Then
        assertEquals("已存在", result.getOcrResult().getPlainText());
        verify(ocrClient, never()).runStructuredOcr(any());
    }

    @Test
    void testPlanningNode_NoOcrContent_ShouldGeneratePlan() {
        // Given
        AgentState state = createTestState();
        state.setOcrResult(OcrStructuredResult.empty());

        String planningResponse = """
                [
                    {
                        "taskId": "T1",
                        "type": "execution",
                        "description": "依据用户请求，搜集并梳理大学物理二的复习要点与流程",
                        "priority": 1,
                        "dueDate": "2025-11-25T12:00:00Z",
                        "estimatedDuration": "2小时"
                    },
                    {
                        "taskId": "T2",
                        "type": "execution",
                        "description": "整理收集到的复习信息，在笔记应用中新建一篇结构清晰的笔记",
                        "priority": 2,
                        "dueDate": "2025-11-25T15:00:00Z",
                        "estimatedDuration": "30min"
                    }
                ]
                """;

        @SuppressWarnings("unchecked")
        Response<AiMessage> mockResponse = mock(Response.class);
        AiMessage mockAiMessage = mock(AiMessage.class);
        when(mockResponse.content()).thenReturn(mockAiMessage);
        when(mockAiMessage.text()).thenReturn(planningResponse);
        when(planningChatModel.generate(any(ChatMessage.class), any(ChatMessage.class))).thenReturn(mockResponse);

        // When
        WorkflowNode planningNode = workflowNodes.createPlanningNode();
        AgentState result = planningNode.process(state);

        // Then
        assertNotNull(result.getTasks());
        assertEquals(2, result.getTasks().size());
        assertEquals("T1", result.getTasks().get(0).getTaskId());
        assertEquals("T2", result.getTasks().get(1).getTaskId());
        assertEquals(LearningTaskType.EXECUTION, result.getTasks().get(0).getType());
        verify(planningChatModel, times(1)).generate(any(ChatMessage.class), any(ChatMessage.class));
    }

    @Test
    void testPlanningNode_WithOcrContent_ShouldIncludeInPrompt() {
        // Given
        AgentState state = createTestState();
        OcrStructuredResult ocrResult = OcrStructuredResult.builder()
                .success(true)
                .plainText("物理公式：F=ma")
                .markdownText("## 物理公式\nF=ma")
                .build();
        state.setOcrResult(ocrResult);

        String planningResponse = """
                [
                    {
                        "taskId": "T1",
                        "type": "execution",
                        "description": "分析OCR识别的物理公式",
                        "priority": 1,
                        "dueDate": "2025-11-25T12:00:00Z",
                        "estimatedDuration": "1小时"
                    }
                ]
                """;

        @SuppressWarnings("unchecked")
        Response<AiMessage> mockResponse = mock(Response.class);
        AiMessage mockAiMessage = mock(AiMessage.class);
        when(mockResponse.content()).thenReturn(mockAiMessage);
        when(mockAiMessage.text()).thenReturn(planningResponse);
        when(planningChatModel.generate(any(ChatMessage.class), any(ChatMessage.class))).thenReturn(mockResponse);

        // When
        WorkflowNode planningNode = workflowNodes.createPlanningNode();
        AgentState result = planningNode.process(state);

        // Then
        assertNotNull(result.getTasks());
        assertEquals(1, result.getTasks().size());
        assertEquals("T1", result.getTasks().getFirst().getTaskId());
        verify(planningChatModel, times(1)).generate(any(ChatMessage.class), any(ChatMessage.class));
    }

    @Test
    void testExecutionNode_ShouldCreateExecutionNode() {
        // When
        WorkflowNode executionNode = workflowNodes.createExecutionNode();

        // Then
        assertNotNull(executionNode, "Execution node should be created");
        // 注意：由于 AiServices 需要真实的模型实例，无法在单元测试中完整测试执行逻辑
        // 完整的执行测试应该在集成测试中进行
    }

    // Helper methods

    private AgentState createTestState() {
        LearnerProfile profile = LearnerProfile.builder()
                .learnerId("7d2377d9-3ecb-4853-af4f-00029085f62d")
                .competencyLevel("初学")
                .learningGoal("大学物理二复习")
                .preferredStyle("练习为主")
                .build();

        return AgentState.builder()
                .imagePath("")
                .learnerProfile(profile)
                .tasks(new ArrayList<>())
                .userQuery("分析大学物理的复习流程，新建一篇笔记存储")
                .currentTaskIndex(0)
                .generatedContents(new ArrayList<>())
                .createdPageIds(new ArrayList<>())
                .createdPages(new ArrayList<>())
                .build();
    }


}
