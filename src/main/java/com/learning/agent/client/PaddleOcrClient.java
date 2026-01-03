package com.learning.agent.client;

import com.learning.agent.dto.client.OcrStructuredResult;

/**
 * PaddleOCR 客户端接口
 */
public interface PaddleOcrClient {

    /**
     * 执行结构化 OCR，输出多种格式文本
     *
     * @param imagePath 输入图片绝对路径
     * @return 结构化 OCR 结果
     */
    OcrStructuredResult runStructuredOcr(String imagePath);
}
