package com.resume_parser.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TextExtractionService {
    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final DOCExtractionService docExtractionService;
    private final PDFExtractionService pdfExtractionService;
    private final GroqService groqService;

    public TextExtractionService(DOCExtractionService docExtractionService,
                                 PDFExtractionService pdfExtractionService,
                                 GroqService groqService) {
        this.docExtractionService = docExtractionService;
        this.pdfExtractionService = pdfExtractionService;
        this.groqService = groqService;
    }

    public String mergeServiceToGetAtsScore(MultipartFile file, String jdText) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded resume file is empty or missing.");
        }

        if (jdText == null || jdText.isBlank()) {
            throw new IllegalArgumentException("Job description (jdText) is required to evaluate ATS score.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Invalid or missing file name.");
        }

        String lowerFileName = fileName.toLowerCase();
        String resumeText;

        if (lowerFileName.endsWith(".docx") || lowerFileName.endsWith(".doc")) {
            resumeText = docExtractionService.textExtractionFromDoc(file);
        } else if (lowerFileName.endsWith(".pdf")) {
            resumeText = pdfExtractionService.extractTextFromPDF(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format (" + fileName + "). Only PDF, DOCX, and DOC are supported.");
        }

        if (resumeText == null || resumeText.isBlank()) {
            throw new IllegalArgumentException("No text could be extracted from the document (" + fileName + "). Please ensure the file is not empty or password protected.");
        }

        log.info("Successfully extracted text from resume. Length: {} chars. Requesting ATS score...", resumeText.length());

        try {
            return groqService.getAtsScore(resumeText, jdText);
        } catch (Exception e) {
            log.error("Error evaluating ATS score via Groq API", e);
            throw new RuntimeException("Failed to evaluate ATS score: " + e.getMessage(), e);
        }
    }

    public java.util.Map<String, Object> checkApiStatus() {
        return groqService.verifyApiKeyStatus();
    }
}
