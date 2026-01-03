package com.learning.agent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面控制器
 * 处理前端页面路由
 */
@Controller
public class PageController {

    /**
     * 首页/落地页
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * 聊天页面
     */
    @GetMapping("/chat.html")
    public String chat() {
        return "chat";
    }
}
