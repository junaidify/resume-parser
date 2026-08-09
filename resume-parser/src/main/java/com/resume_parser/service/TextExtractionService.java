package com.resume_parser.service;

import org.springframework.web.multipart.MultipartFile;

public class TextExtractionService {
    private final DOCExtractionService docExtractionService;
    private final PDFExtractionService pdfExtractionService;
    private final GroqService groqService;

    public TextExtractionService(DOCExtractionService docExtractionService, PDFExtractionService pdfExtractionService,
                                 GroqService groqService){
        this.docExtractionService = docExtractionService;
        this.pdfExtractionService = pdfExtractionService;
        this.groqService = groqService;
    }

    public String mergeServiceToGetAtsScore(MultipartFile file, String jdText) throws Exception{
        String resumeText = "";

        String fileName = file.getOriginalFilename();

        if(fileName.endsWith(".docx") || fileName.endsWith(".doc")){
            resumeText = docExtractionService.textExtractionFromDoc(file);
        }else if(fileName.endsWith(".pdf")){
            resumeText = pdfExtractionService.extractTextFromPDF(file);
        }else{
            throw new IllegalArgumentException("Unsupported file format. Only PDF, Docx and Doc are supported.");
        }

        if(resumeText.isEmpty()){
            throw new IllegalArgumentException("Unsupported file format. Only PDF, Docx and Doc are supported.");
        }

        try{
            return groqService.getAtsScore(resumeText, jdText);
        }catch(Exception e){
            throw new RuntimeException("Failed to get ATS score" + e.getMessage());
        }

    }

}
