package com.resume_parser.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class PDFExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PDFExtractionService.class);

    public String extractTextFromPDF(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is empty or null.");
        }

        log.info("Extracting text from PDF file: {}", file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            
            PDFTextStripper stripper = new PDFTextStripper();
            String extractedText = stripper.getText(document);

            if (extractedText == null || extractedText.isBlank()) {
                log.warn("PDF extraction returned empty text for file: {}", file.getOriginalFilename());
            }

            return extractedText != null ? extractedText.trim() : "";
        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", file.getOriginalFilename(), e);
            throw new IOException("Error reading or parsing PDF file: " + e.getMessage(), e);
        }
    }
}

