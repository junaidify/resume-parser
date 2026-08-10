package com.resume_parser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume_parser.AppConfig.WebClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class GroqService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${groq.base.url}")
    private String groqBaseUri;

    GroqService(WebClient webClient) {
        this.webClient = webClient;
    }

    public String getAtsScore(String resumeText, String jdText) throws Exception {
        String systemRole = "You're an export ATS(Applicant Tracking System) evaluator.";


        String userPrompt = """
                Analyze the resume against job description. 
                
                Job Description: 
                %s
                
                Resume: 
                %s
                
                Return output in JSON format. No description, No markdown, Pure JSON: 
                {
                  "jd_required_skills": [],
                   "jd_preferred_skills": [],
                   "jd_experience_years": 0,
                   "resume_skills": [],
                   "matched_required": [],
                   "matched_preferred": [],
                   "missing_required": [],
                   "missing_preferred": [],
                   "ats_score": 0
                """.formatted(jdText, resumeText);

        Map<String, Object> requestBody = Map.of(
                "model", "llama3-70b-8192",
                "message", List.of(
                        Map.of("role", "system", "content", systemRole),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        String rawResponse = webClient.post()
                .uri(groqBaseUri)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(rawResponse);
        return root.path("choices").get(0).path("message").path("content").asText();
    }


}
