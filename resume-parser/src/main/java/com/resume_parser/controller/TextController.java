package com.resume_parser.controller;

import com.resume_parser.service.TextExtractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/parse")
public class TextController{
    private final TextExtractionService textExtractionService;

    public TextController(TextExtractionService textExtractionService){
        this.textExtractionService = textExtractionService;
    }

    @PostMapping("/resume")
    public ResponseEntity<String> parseResume(@RequestParam("file") MultipartFile file, String jdText) throws Exception{
        return ResponseEntity.ok(textExtractionService.mergeServiceToGetAtsScore(file, jdText));
    }
}