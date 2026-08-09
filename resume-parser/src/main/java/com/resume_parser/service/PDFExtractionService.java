package com.resume_parser.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class PDFExtractionService {

    public String extractTextFromPDF(MultipartFile file){
        String parsedString = "";

        try(PDDocument document = Loader.loadPDF(file.getBytes())){
            PDFTextStripper stripper = new PDFTextStripper();
            parsedString = stripper.getText(document);

        }catch(IOException e){
            e.printStackTrace();
        }
        
        return parsedString; 
    }

}
