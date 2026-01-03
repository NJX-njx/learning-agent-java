package com.learning.agent.workflow;

import com.learning.agent.model.LearnerProfile;
import com.learning.agent.model.LearningTask;
import com.learning.agent.model.LearningTaskType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AgentWorkflow 单元测试
 * 测试完整的工作流编排
 */
@ExtendWith(MockitoExtension.class)
class AgentWorkflowTest {

    @Mock
    private WorkflowNodes workflowNodes;

    @Mock
    private WorkflowNode ocrNode;

    @Mock
    private WorkflowNode planningNode;

    @Mock
    private WorkflowNode executionNode;

    private AgentWorkflow agentWorkflow;

    @BeforeEach
    void setUp() {
        agentWorkflow = new AgentWorkflow(workflowNodes);
    }

    @Test
    void testWorkflow_CompleteExecution_NoTasks() {
        // Given
        AgentState initialState = createInitialState();

        AgentState afterPlanning = createInitialState();
        afterPlanning.setTasks(new ArrayList<>()); // 空任务列表

        when(workflowNodes.createOcrNode()).thenReturn(ocrNode);
        when(workflowNodes.createPlanningNode()).thenReturn(planningNode);
        when(ocrNode.process(any())).thenReturn(initialState);
        when(planningNode.process(any())).thenReturn(afterPlanning);

        // When
        AgentState result = agentWorkflow.invoke(initialState);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getTasks().size());
        verify(ocrNode, times(1)).process(any());
        verify(planningNode, times(1)).process(any());
        verify(workflowNodes, never()).createExecutionNode();
    }

    @Test
    void testWorkflow_CompleteExecution_WithTasks() {
        // Given
        AgentState initialState = createInitialState();

        AgentState afterOcr = createInitialState();

        List<LearningTask> tasks = createTestTasks(2);
        AgentState afterPlanning = createInitialState();
        afterPlanning.setTasks(tasks);
        afterPlanning.setCurrentTaskIndex(0); // 确保从 0 开始

        AgentState afterFirstExecution = createInitialState();
        afterFirstExecution.setTasks(tasks);
        afterFirstExecution.setCurrentTaskIndex(1);

        AgentState afterSecondExecution = createInitialState();
        afterSecondExecution.setTasks(tasks);
        afterSecondExecution.setCurrentTaskIndex(2);

        when(workflowNodes.createOcrNode()).thenReturn(ocrNode);
        when(workflowNodes.createPlanningNode()).thenReturn(planningNode);
        when(workflowNodes.createExecutionNode()).thenReturn(executionNode);

        when(ocrNode.process(any())).thenReturn(afterOcr);
        when(planningNode.process(any())).thenReturn(afterPlanning);
        when(executionNode.process(any()))
                .thenReturn(afterFirstExecution)
                .thenReturn(afterSecondExecution);

        // When
        AgentState result = agentWorkflow.invoke(initialState);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getTasks().size());
        assertEquals(2, result.getCurrentTaskIndex());
        verify(ocrNode, times(1)).process(any());
        verify(planningNode, times(1)).process(any());
        verify(executionNode, times(2)).process(any());
    }

    @Test
    void testWorkflow_CompleteExecution_SingleTask() {
        // Given
        AgentState initialState = createInitialState();

        AgentState afterOcr = createInitialState();

        List<LearningTask> tasks = createTestTasks(1);
        AgentState afterPlanning = createInitialState();
        afterPlanning.setTasks(tasks);
        afterPlanning.setCurrentTaskIndex(0); // 确保从 0 开始

        AgentState afterExecution = createInitialState();
        afterExecution.setTasks(tasks);
        afterExecution.setCurrentTaskIndex(1);

        when(workflowNodes.createOcrNode()).thenReturn(ocrNode);
        when(workflowNodes.createPlanningNode()).thenReturn(planningNode);
        when(workflowNodes.createExecutionNode()).thenReturn(executionNode);

        when(ocrNode.process(any())).thenReturn(afterOcr);
        when(planningNode.process(any())).thenReturn(afterPlanning);
        when(executionNode.process(any())).thenReturn(afterExecution);

        // When
        AgentState result = agentWorkflow.invoke(initialState);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getTasks().size());
        assertEquals(1, result.getCurrentTaskIndex());
        verify(executionNode, times(1)).process(any());
    }

    // Helper methods

    private AgentState createInitialState() {
        LearnerProfile profile = LearnerProfile.builder()
                .learnerId("test-learner-id")
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

    private List<LearningTask> createTestTasks(int count) {
        List<LearningTask> tasks = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            tasks.add(LearningTask.builder()
                    .taskId("T" + i)
                    .type(LearningTaskType.EXECUTION)
                    .description("任务 " + i)
                    .priority(i)
                    .dueDate("2025-11-25T12:00:00Z")
                    .estimatedDuration(i + "小时")
                    .build());
        }
        return tasks;
    }
}
