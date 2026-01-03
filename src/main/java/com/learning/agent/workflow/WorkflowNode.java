package com.learning.agent.workflow;

/**
 * 工作流节点接口
 * 每个节点处理状态并返回更新后的状态
 */
@FunctionalInterface
public interface WorkflowNode {

    /**
     * 处理当前状态
     *
     * @param state 输入状态
     * @return 更新后的状态
     */
    AgentState process(AgentState state);
}
