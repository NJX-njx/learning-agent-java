package com.learning.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学习者画像信息
 * 用于决策层的个性化策略
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearnerProfile {

    /**
     * 学习者唯一标识
     */
    private String learnerId;

    /**
     * 当前掌握水平标签
     */
    private String competencyLevel;

    /**
     * 近期学习目标
     */
    private String learningGoal;

    /**
     * 偏好的学习方式（讲解、练习、计划）
     */
    private String preferredStyle;

    /**
     * 创建默认学习者画像
     */
    public static LearnerProfile defaultProfile(String learnerId) {
        return LearnerProfile.builder()
                .learnerId(learnerId)
                .competencyLevel("中等")
                .learningGoal("巩固知识点")
                .preferredStyle("讲解+计划")
                .build();
    }
}
