package com.learning.agent.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 整页 OCR 结构化结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrStructuredResult {

    /**
     * OCR 是否成功
     */
    private boolean success;

    /**
     * 输入图片的绝对路径
     */
    private String originalPath;

    /**
     * 全部文本的纯文本拼接形式
     */
    private String plainText;

    /**
     * 带格式的 Markdown 文本，用于 Notion 或笔记
     */
    private String markdownText;

    /**
     * 表格形式的数据集合，支持课表或表格题
     */
    private List<List<String>> tableData;

    /**
     * 逐行的 OCR 文本片段列表
     */
    private List<OcrTextSpan> spans;

    /**
     * 创建空的 OCR 结果
     */
    public static OcrStructuredResult empty() {
        return OcrStructuredResult.builder()
                .success(true)
                .originalPath("")
                .plainText("")
                .markdownText("")
                .tableData(List.of())
                .spans(List.of())
                .build();
    }

    /**
     * 创建失败的 OCR 结果
     */
    public static OcrStructuredResult failure(String imagePath, String errorMessage) {
        return OcrStructuredResult.builder()
                .success(false)
                .originalPath(imagePath)
                .plainText("")
                .markdownText("(OCR Failed: " + errorMessage + ")")
                .tableData(List.of())
                .spans(List.of())
                .build();
    }
}
