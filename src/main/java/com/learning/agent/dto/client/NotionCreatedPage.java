package com.learning.agent.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建的页面信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotionCreatedPage {

    /**
     * 页面 ID
     */
    private String id;

    /**
     * 页面 URL（可选）
     */
    private String url;
}
