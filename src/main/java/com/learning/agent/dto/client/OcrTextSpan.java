package com.learning.agent.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * OCR 文本片段 - 包含坐标与文本内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrTextSpan {

    /**
     * 行标识，方便追踪段落顺序
     */
    private String lineId;

    /**
     * 实际识别出来的文本字符串
     */
    private String text;

    /**
     * PaddleOCR 返回的置信度分数
     */
    private double confidence;

    /**
     * 以 Array 表示的矩形坐标 [x1,y1,x2,y2]
     */
    private List<Double> boundingBox;

    /**
     * 语义分类（题干、答案、批注等）
     */
    private String classification;

    /**
     * 来源元数据，例如页码、拍摄时间
     */
    private Map<String, String> sourceMeta;
}
