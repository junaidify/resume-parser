package com.resume_parser.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TextExtractionServiceTest {

    @Mock
    private DOCExtractionService docExtractionService;

    @Mock
    private PDFExtractionService pdfExtractionService;

    @Mock
    private GroqService groqService;

    private TextExtractionService textExtractionService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionService(docExtractionService, pdfExtractionService, groqService);
    }

    @Test
    void testMergeServiceWithPdfSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "Sample PDF Content".getBytes());
        String jdText = "Software Engineer with Java skills";

        when(pdfExtractionService.extractTextFromPDF(any())).thenReturn("Java Developer with 5 years experience");
        when(groqService.getAtsScore(any(), any())).thenReturn("{\"ats_score\": 85}");

        String result = textExtractionService.mergeServiceToGetAtsScore(file, jdText);

        assertNotNull(result);
        assertTrue(result.contains("ats_score"));
    }

    @Test
    void testMergeServiceWithDocxSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Sample Word Content".getBytes());
        String jdText = "Software Engineer with Java skills";

        when(docExtractionService.textExtractionFromDoc(any())).thenReturn("Java Developer with 5 years experience");
        when(groqService.getAtsScore(any(), any())).thenReturn("{\"ats_score\": 90}");

        String result = textExtractionService.mergeServiceToGetAtsScore(file, jdText);

        assertNotNull(result);
        assertTrue(result.contains("ats_score"));
    }

    @Test
    void testMergeServiceWithUnsupportedFormatThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "Text content".getBytes());
        String jdText = "Software Engineer";

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> textExtractionService.mergeServiceToGetAtsScore(file, jdText)
        );

        assertTrue(exception.getMessage().contains("Unsupported file format"));
    }

    @Test
    void testMergeServiceWithEmptyFileThrowsException() {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);
        String jdText = "Software Engineer";

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> textExtractionService.mergeServiceToGetAtsScore(file, jdText)
        );

        assertTrue(exception.getMessage().contains("empty or missing"));
    }
}
