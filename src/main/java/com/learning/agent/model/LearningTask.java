package com.learning.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学习任务
 * 表示一次流程要执行的任务单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningTask {

    /**
     * 唯一任务编号
     */
    private String taskId;

    /**
     * 任务类型
     */
    private LearningTaskType type;

    /**
     * 人类可读的任务描述
     */
    private String description;

    /**
     * 优先级，数值越大越紧急 (1-5)
     */
    private int priority;

    /**
     * 任务完成的截止日期（ISO 字符串）
     */
    private String dueDate;

    /**
     * 预估耗时
     */
    private String estimatedDuration;
}
