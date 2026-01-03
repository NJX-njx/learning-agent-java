package com.learning.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.agent.client.NotionClient;
import com.learning.agent.dto.web.AnalyzeResponse;
import com.learning.agent.dto.web.AnalyzeResponse.AnalyzeData;
import com.learning.agent.dto.web.AnalyzeResponse.Step;
import com.learning.agent.workflow.AgentWorkflow;
import com.learning.agent.workflow.AgentState;
import com.learning.agent.model.LearnerProfile;
import com.learning.agent.model.LearningTask;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 分析服务
 * 处理图片/文本分析请求，协调工作流执行
 */
@Slf4j
@Service
public class AnalyzeService {

    private final AgentWorkflow workflow;
    private final NotionClient notionClient;
    private final ObjectMapper objectMapper;

    private static final String UPLOAD_DIR = "uploads";

    public AnalyzeService(AgentWorkflow workflow, NotionClient notionClient, ObjectMapper objectMapper) {
        this.workflow = workflow;
        this.notionClient = notionClient;
        this.objectMapper = objectMapper;

        // 确保上传目录存在
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }

    /**
     * 分析请求
     */
    public AnalyzeResponse analyze(MultipartFile image, String message, String profileJson, String learnerId) {
        try {
            // 1. 保存上传的图片
            String imagePath = "";
            if (image != null && !image.isEmpty()) {
                imagePath = saveUploadedFile(image);
                log.info("Image saved: {}", imagePath);
            }

            log.info("Processing request");
            if (!imagePath.isEmpty()) log.info("Image: {}", imagePath);
            log.info("User Query: {}", message);
            log.info("Learner ID: {}", learnerId);

            // 2. 解析学习者画像
            LearnerProfile learnerProfile = parseLearnerProfile(profileJson, learnerId);
            log.info("Learner Profile: {}", learnerProfile);

            // 3. 查找父页面
            String parentPageId = findParentPage();
            if (learnerProfile.getLearnerId() == null || learnerProfile.getLearnerId().isEmpty()) {
                learnerProfile.setLearnerId(parentPageId);
            }

            // 4. 构建初始状态
            AgentState initialState = AgentState.builder()
                    .imagePath(imagePath)
                    .learnerProfile(learnerProfile)
                    .tasks(new ArrayList<>())
                    .userQuery(message != null ? message : "")
                    .currentTaskIndex(0)
                    .generatedContents(new ArrayList<>())
                    .createdPageIds(new ArrayList<>())
                    .createdPages(new ArrayList<>())
                    .build();

            // 5. 执行工作流
            AgentState finalState = workflow.invoke(initialState);

            // 6. 构建响应
            List<Step> steps = buildSteps(finalState);

            AnalyzeData data = AnalyzeData.builder()
                    .pageIds(finalState.getCreatedPageIds())
                    .createdPages(finalState.getCreatedPages())
                    .contents(finalState.getGeneratedContents())
                    .steps(steps)
                    .build();

            return AnalyzeResponse.success(data);

        } catch (Exception e) {
            log.error("Analysis failed", e);
            return AnalyzeResponse.error(e.getMessage());
        }
    }

    private String saveUploadedFile(MultipartFile file) throws IOException {
        String filename = System.currentTimeMillis() + "-" + file.getOriginalFilename();
        Path path = Paths.get(UPLOAD_DIR, filename);
        Files.write(path, file.getBytes());
        return path.toAbsolutePath().toString();
    }

    private LearnerProfile parseLearnerProfile(String profileJson, String learnerId) {
        LearnerProfile profile = LearnerProfile.defaultProfile(learnerId != null ? learnerId : "");

        if (profileJson != null && !profileJson.isEmpty()) {
            try {
                Map<String, Object> profileData = objectMapper.readValue(profileJson, new TypeReference<>() {
                });
                if (profileData.get("competencyLevel") != null) {
                    profile.setCompetencyLevel((String) profileData.get("competencyLevel"));
                }
                if (profileData.get("learningGoal") != null) {
                    profile.setLearningGoal((String) profileData.get("learningGoal"));
                }
                if (profileData.get("preferredStyle") != null) {
                    profile.setPreferredStyle((String) profileData.get("preferredStyle"));
                }
            } catch (Exception e) {
                log.warn("Failed to parse profile JSON: {}", e.getMessage());
            }
        }

        return profile;
    }

    private String findParentPage() {
        try {
            // 优先搜索 Learning Dashboard
            Optional<NotionClient.SearchResult> dashboard = notionClient.searchPage("Learning Dashboard");
            if (dashboard.isPresent()) {
                return dashboard.get().id();
            }

            // 回退：搜索任意页面
            Optional<NotionClient.SearchResult> anyPage = notionClient.searchPage("");
            if (anyPage.isPresent()) {
                return anyPage.get().id();
            }

            throw new RuntimeException("No Notion pages found");
        } catch (Exception e) {
            log.error("Failed to find parent page", e);
            throw new RuntimeException("Failed to find parent page", e);
        }
    }

    private List<Step> buildSteps(AgentState state) {
        List<Step> steps = new ArrayList<>();

        // 为每个执行的任务添加步骤
        for (int i = 0; i < state.getTasks().size(); i++) {
            LearningTask task = state.getTasks().get(i);
            steps.add(Step.builder()
                    .title(task.getDescription() != null ? task.getDescription() : "Task " + (i + 1))
                    .status("completed")
                    .duration("2s")
                    .details(String.format("Type: %s. Priority: %d.", task.getType().getValue(), task.getPriority()))
                    .build());
        }

        // 如果创建了页面，添加 Notion Sync 步骤
        if (!state.getCreatedPageIds().isEmpty()) {
            steps.add(Step.builder()
                    .title("Notion Sync")
                    .status("completed")
                    .duration("1s")
                    .details("Saved to Notion pages: " + String.join(", ", state.getCreatedPageIds()))
                    .build());
        }

        return steps;
    }
}
