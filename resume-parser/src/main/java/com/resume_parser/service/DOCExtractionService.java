package com.resume_parser.service;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class DOCExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DOCExtractionService.class);

    public String textExtractionFromDoc(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Word document file is empty or null.");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null) {
            throw new IllegalArgumentException("File name cannot be null.");
        }

        String lowerFileName = fileName.toLowerCase();
        log.info("Extracting text from Word document: {}", fileName);

        try (InputStream inputStream = file.getInputStream()) {
            String extractedText = "";

            if (lowerFileName.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(inputStream);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    extractedText = extractor.getText();
                }
            } else if (lowerFileName.endsWith(".doc")) {
                try (HWPFDocument document = new HWPFDocument(inputStream);
                     WordExtractor extractor = new WordExtractor(document)) {
                    extractedText = extractor.getText();
                }
            } else {
                throw new IllegalArgumentException("Unsupported Word document format for file: " + fileName);
            }

            return extractedText != null ? extractedText.trim() : "";
        } catch (Exception e) {
            log.error("Failed to parse Word document: {}", fileName, e);
            throw new IOException("Error parsing Word document (" + fileName + "): " + e.getMessage(), e);
        }
    }
}

