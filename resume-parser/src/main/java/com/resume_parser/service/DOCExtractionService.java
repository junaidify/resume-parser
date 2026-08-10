package com.resume_parser.service;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class DOCExtractionService {

    public String textExtractionFromDoc(MultipartFile file){
        String parseString = "";
        String fileName = file.getOriginalFilename();

        if(fileName == null){
            return parseString;
        }

        try(InputStream inputStream = file.getInputStream()){

            if(fileName.endsWith(".docx")){
                try(XWPFDocument doc = new XWPFDocument(inputStream);
                    XWPFWordExtractor extractor = new XWPFWordExtractor(doc)){
                    parseString = extractor.getText();
                }
            }
            else if(fileName.endsWith(".doc")){
                try(HWPFDocument document = new HWPFDocument(inputStream);
                    WordExtractor extractor = new WordExtractor(document)){
                    parseString = extractor.getText();
                }
            }
        }catch(IOException e){
            e.printStackTrace();
        }

        return parseString;
    }
}
