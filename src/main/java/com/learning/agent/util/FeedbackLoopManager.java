package com.learning.agent.util;

import com.learning.agent.model.LearningTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 反馈循环管理器
 * 收集用户反馈，指导下一轮策略
 */
@Slf4j
@Component
public class FeedbackLoopManager {

    private final List<FeedbackRecord> records = new ArrayList<>();

    /**
     * 反馈记录
     */
    public record FeedbackRecord(
            String taskId,
            double rating,
            String comment,
            String timestamp
    ) {
    }

    /**
     * 记录反馈
     */
    public void recordFeedback(LearningTask task, double rating, String comment) {
        log.debug("recordFeedback called: {}", task.getTaskId());

        FeedbackRecord record = new FeedbackRecord(
                task.getTaskId(),
                rating,
                comment,
                Instant.now().toString()
        );

        records.add(record);
        log.debug("Total feedback count: {}", records.size());
    }

    /**
     * 计算平均评分
     */
    public double getAverageRating() {
        log.debug("Computing average rating from {} records", records.size());

        if (records.isEmpty()) {
            return 0;
        }

        double sum = records.stream()
                .mapToDouble(FeedbackRecord::rating)
                .sum();

        double average = sum / records.size();
        log.debug("Average rating: {}", average);

        return average;
    }

    /**
     * 根据平均评分返回下一轮策略提示
     */
    public String getStrategyNote() {
        double avg = getAverageRating();

        if (avg >= 4) {
            return "保持当前策略，适度增加挑战难度。";
        }
        if (avg >= 2.5) {
            return "需要补充更多讲解细节并缩短反馈周期。";
        }
        return "立即回顾提示模板与计划生成规则，查找痛点。";
    }

    /**
     * 获取所有反馈记录
     */
    public List<FeedbackRecord> getAllRecords() {
        return new ArrayList<>(records);
    }

    /**
     * 清空反馈记录
     */
    public void clearRecords() {
        records.clear();
        log.debug("Feedback records cleared");
    }
}
