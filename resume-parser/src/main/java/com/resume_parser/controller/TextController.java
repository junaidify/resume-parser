package com.resume_parser.controller;

import com.resume_parser.service.TextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/parse")
public class TextController{
    private TextService textService;

    public TextController(TextService textService){
        this.textService = textService;
    }

    @PostMapping("/resume")
    public ResponseEntity<String> parseResume(@RequestParam("file") MultipartFile file){
        return ResponseEntity.ok(textService.)
    }
}