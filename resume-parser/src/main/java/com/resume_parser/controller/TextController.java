package com.resume_parser.controller;

import com.resume_parser.service.TextExtractionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/parse")
public class TextController {

    private final TextExtractionService textExtractionService;

    public TextController(TextExtractionService textExtractionService) {
        this.textExtractionService = textExtractionService;
    }

    @PostMapping(value = "/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> parseResume(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jdText", required = false, defaultValue = "") String jdText) throws Exception {
        String result = textExtractionService.mergeServiceToGetAtsScore(file, jdText);
        return ResponseEntity.ok(result);
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getApiStatus() {
        return ResponseEntity.ok(textExtractionService.checkApiStatus());
    }
}
