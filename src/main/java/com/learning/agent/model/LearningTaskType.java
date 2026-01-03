package com.learning.agent.model;

import lombok.Getter;

/**
 * 学习任务类型枚举
 */
@Getter
public enum LearningTaskType {

    /**
     * 对知识点、题目进行标注或解释
     */
    ANNOTATION("annotation"),

    /**
     * 深入分析内容中的概念、解题思路或易错点
     */
    ANALYSIS("analysis"),

    /**
     * 对内容进行分类、整理、总结，形成结构化笔记
     */
    ORGANIZATION("organization"),

    /**
     * 制定后续学习计划，如每日任务安排、复习周期等
     */
    PLANNING("planning"),

    /**
     * 执行具体操作，如创建Notion页面、写入内容、生成练习题等
     */
    EXECUTION("execution");

    private final String value;

    LearningTaskType(String value) {
        this.value = value;
    }

    public static LearningTaskType fromValue(String value) {
        for (LearningTaskType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return ANALYSIS; // 默认返回分析类型
    }
}
