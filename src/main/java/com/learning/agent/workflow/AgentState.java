package com.learning.agent.workflow;

import com.learning.agent.dto.client.NotionCreatedPage;
import com.learning.agent.dto.client.OcrStructuredResult;
import com.learning.agent.model.LearnerProfile;
import com.learning.agent.model.LearningTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 状态
 * 智能体在工作流中的完整状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentState {

    // --- Inputs (输入) ---

    /**
     * 输入图片的路径
     */
    private String imagePath;

    /**
     * 学习者画像
     */
    private LearnerProfile learnerProfile;

    /**
     * 待执行的任务列表
     */
    @Builder.Default
    private List<LearningTask> tasks = new ArrayList<>();

    /**
     * 用户输入的查询 (可选)
     */
    private String userQuery;

    // --- Internal State (内部状态) ---

    /**
     * OCR 识别结果 (由 ocrNode 填充)
     */
    private OcrStructuredResult ocrResult;

    /**
     * 当前正在处理的任务索引
     */
    @Builder.Default
    private int currentTaskIndex = 0;

    // --- Outputs (输出) ---

    /**
     * 针对每个任务生成的 Markdown 内容
     */
    @Builder.Default
    private List<String> generatedContents = new ArrayList<>();

    /**
     * 在 Notion 中创建的页面 ID 列表
     */
    @Builder.Default
    private List<String> createdPageIds = new ArrayList<>();

    /**
     * 在 Notion 中创建的页面列表（包含可选 URL），用于在前端展示可点击链接
     */
    @Builder.Default
    private List<NotionCreatedPage> createdPages = new ArrayList<>();

    /**
     * 检查是否还有更多任务需要执行
     */
    public boolean hasMoreTasks() {
        return currentTaskIndex < tasks.size();
    }

    /**
     * 获取当前任务
     */
    public LearningTask getCurrentTask() {
        if (currentTaskIndex < tasks.size()) {
            return tasks.get(currentTaskIndex);
        }
        return null;
    }

    /**
     * 移动到下一个任务
     */
    public void moveToNextTask() {
        currentTaskIndex++;
    }

    /**
     * 添加生成的内容
     */
    public void addGeneratedContent(String content) {
        if (generatedContents == null) {
            generatedContents = new ArrayList<>();
        }
        generatedContents.add(content);
    }

    /**
     * 添加创建的页面
     */
    public void addCreatedPage(String pageId, String url) {
        if (createdPageIds == null) {
            createdPageIds = new ArrayList<>();
        }
        if (createdPages == null) {
            createdPages = new ArrayList<>();
        }
        createdPageIds.add(pageId);
        createdPages.add(NotionCreatedPage.builder().id(pageId).url(url).build());
    }
}
