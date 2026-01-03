package com.learning.agent.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工作流执行器
 * 类似 LangGraph 的工作流编排，按顺序执行节点
 */
@Slf4j
@Component
public class AgentWorkflow {

    private final WorkflowNodes nodes;

    public AgentWorkflow(WorkflowNodes nodes) {
        this.nodes = nodes;
    }

    /**
     * 执行完整的工作流
     * OCR -> Planning -> Execution (循环直到所有任务完成)
     */
    public AgentState invoke(AgentState initialState) {
        log.info("=== Starting Agent Workflow ===");

        AgentState state = initialState;

        // 1. OCR Node
        state = nodes.createOcrNode().process(state);

        // 2. Planning Node
        state = nodes.createPlanningNode().process(state);

        // 3. Execution Node (循环执行直到所有任务完成)
        if (state.hasMoreTasks()) {
            WorkflowNode executionNode = nodes.createExecutionNode();
            while (state.hasMoreTasks()) {
                state = executionNode.process(state);
            }
        }

        log.info("=== Workflow Completed ===");
        log.info("Tasks executed: {}", state.getTasks().size());
        log.info("Pages created: {}", state.getCreatedPageIds().size());

        return state;
    }
}
