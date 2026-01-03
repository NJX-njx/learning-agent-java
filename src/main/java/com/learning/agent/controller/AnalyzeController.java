package com.learning.agent.controller;

import com.learning.agent.dto.web.AnalyzeResponse;
import com.learning.agent.service.AnalyzeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/")
public class AnalyzeController {

    private final AnalyzeService analyzeService;

    public AnalyzeController(AnalyzeService analyzeService) {
        this.analyzeService = analyzeService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnalyzeResponse> analyze(
            @RequestParam(value = "image", required = false) MultipartFile image,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "profile", required = false) String profile,
            @RequestParam(value = "learnerId", required = false) String learnerId) {

        log.info("Analyze request received");

        if ((image == null || image.isEmpty()) && (message == null || message.isEmpty())) {
            return ResponseEntity.badRequest()
                    .body(AnalyzeResponse.error("No image file or message provided"));
        }

        AnalyzeResponse response = analyzeService.analyze(image, message, profile, learnerId);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
