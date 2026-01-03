package com.learning.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.workflow.AgentState;
import com.learning.agent.client.NotionClient;
import com.learning.agent.dto.web.AnalyzeResponse;
import com.learning.agent.workflow.AgentWorkflow;
import com.learning.agent.model.*;
import com.learning.agent.service.AnalyzeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AnalyzeService 单元测试
 * 测试分析服务的核心功能
 */
@ExtendWith(MockitoExtension.class)
class AnalyzeServiceTest {

    @Mock
    private AgentWorkflow workflow;

    @Mock
    private NotionClient notionClient;

    @Mock
    private MultipartFile mockImageFile;

    private AnalyzeService analyzeService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        analyzeService = new AnalyzeService(workflow, notionClient, objectMapper);

        // 确保上传目录存在
        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }

    @Test
    void testAnalyze_WithoutImage_ShouldProcessSuccessfully() {
        // Given
        String message = "分析大学物理的复习流程，新建一篇笔记存储";
        String learnerId = "7d2377d9-3ecb-4853-af4f-00029085f62d";
        String profileJson = """
                {
                    "learnerId": "7d2377d9-3ecb-4853-af4f-00029085f62d",
                    "competencyLevel": "初学",
                    "learningGoal": "大学物理二复习",
                    "preferredStyle": "练习为主"
                }
                """;

        NotionClient.SearchResult searchResult = new NotionClient.SearchResult("2dc91784-9e6e-8038-9d6e-d5b9644d5e6b", "sophie");
        when(notionClient.searchPage("Learning Dashboard")).thenReturn(Optional.empty());
        when(notionClient.searchPage("")).thenReturn(Optional.of(searchResult));

        AgentState finalState = createFinalState();
        when(workflow.invoke(any(AgentState.class))).thenReturn(finalState);

        // When
        AnalyzeResponse response = analyzeService.analyze(null, message, profileJson, learnerId);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData());
        assertEquals(2, response.getData().getSteps().size());
        verify(workflow, times(1)).invoke(any(AgentState.class));
        verify(notionClient, times(2)).searchPage(anyString());
    }

    @Test
    void testAnalyze_WithImage_ShouldSaveAndProcess() throws Exception {
        // Given
        String message = "分析物理公式";
        String learnerId = "test-learner-id";
        String profileJson = """
                {
                    "learnerId": "test-learner-id",
                    "competencyLevel": "初学",
                    "learningGoal": "学习物理",
                    "preferredStyle": "理论为主"
                }
                """;

        when(mockImageFile.isEmpty()).thenReturn(false);
        when(mockImageFile.getOriginalFilename()).thenReturn("test.jpg");
        when(mockImageFile.getBytes()).thenReturn(new byte[]{1, 2, 3});

        NotionClient.SearchResult searchResult = new NotionClient.SearchResult("parent-page-id", "Test Page");
        when(notionClient.searchPage(anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(searchResult));

        AgentState finalState = createFinalState();
        when(workflow.invoke(any(AgentState.class))).thenReturn(finalState);

        // When
        AnalyzeResponse response = analyzeService.analyze(mockImageFile, message, profileJson, learnerId);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        verify(mockImageFile, atLeastOnce()).isEmpty();
        verify(workflow, times(1)).invoke(any(AgentState.class));
    }

    @Test
    void testAnalyze_WithEmptyProfileJson_ShouldUseDefaultProfile() {
        // Given
        String message = "测试消息";
        String learnerId = "test-id";

        NotionClient.SearchResult searchResult = new NotionClient.SearchResult("page-id", "Test");
        when(notionClient.searchPage(anyString()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(searchResult));

        AgentState finalState = createFinalState();
        when(workflow.invoke(any(AgentState.class))).thenReturn(finalState);

        // When
        AnalyzeResponse response = analyzeService.analyze(null, message, "", learnerId);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        verify(workflow, times(1)).invoke(any(AgentState.class));
    }

    @Test
    void testAnalyze_WorkflowCreatesPages_ShouldReturnPageIds() {
        // Given
        String message = "创建笔记";
        String learnerId = "test-id";
        String profileJson = "{}";

        NotionClient.SearchResult searchResult = new NotionClient.SearchResult("parent-id", "Parent");
        when(notionClient.searchPage(anyString())).thenReturn(Optional.of(searchResult));

        AgentState finalState = createFinalStateWithPages();
        when(workflow.invoke(any(AgentState.class))).thenReturn(finalState);

        // When
        AnalyzeResponse response = analyzeService.analyze(null, message, profileJson, learnerId);

        // Then
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertNotNull(response.getData().getPageIds());
        assertEquals(2, response.getData().getPageIds().size());
        assertEquals(2, response.getData().getCreatedPages().size());
    }

    // Helper methods

    private AgentState createFinalState() {
        LearnerProfile profile = LearnerProfile.builder()
                .learnerId("7d2377d9-3ecb-4853-af4f-00029085f62d")
                .competencyLevel("初学")
                .learningGoal("大学物理二复习")
                .preferredStyle("练习为主")
                .build();

        List<LearningTask> tasks = new ArrayList<>();
        tasks.add(LearningTask.builder()
                .taskId("T1")
                .type(LearningTaskType.EXECUTION)
                .description("任务1描述")
                .priority(1)
                .build());
        tasks.add(LearningTask.builder()
                .taskId("T2")
                .type(LearningTaskType.EXECUTION)
                .description("任务2描述")
                .priority(2)
                .build());

        List<String> contents = new ArrayList<>();
        contents.add("任务1完成");
        contents.add("任务2完成");

        return AgentState.builder()
                .learnerProfile(profile)
                .tasks(tasks)
                .generatedContents(contents)
                .createdPageIds(new ArrayList<>())
                .createdPages(new ArrayList<>())
                .currentTaskIndex(2)
                .build();
    }

    private AgentState createFinalStateWithPages() {
        AgentState state = createFinalState();

        state.addCreatedPage("page-id-1", "https://notion.so/page1");
        state.addCreatedPage("page-id-2", "https://notion.so/page2");

        return state;
    }
}
