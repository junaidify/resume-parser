package com.resume_parser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.base.url}")
    private String groqBaseUrl;

    public GroqService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String getAtsScore(String resumeText, String jdText) throws Exception {
        String systemRole = "You're an expert ATS (Applicant Tracking System) evaluator.";

        String userPromptTemplate = """
                Analyze the resume against the job description.
                
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
                }
                """;

        // Avoid String.format / .formatted() because resume/JD text may contain '%' signs
        String userPrompt = userPromptTemplate
                .replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(jdText))
                .replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(resumeText));

        Map<String, Object> requestBody = Map.of(
                "model", "llama3-70b-8192",
                "messages", List.of(
                        Map.of("role", "system", "content", systemRole),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        log.info("Sending ATS evaluation request to Groq API...");

        String rawResponse = webClient.post()
                .uri(groqBaseUrl)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Received empty response from Groq API.");
        }

        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode choices = root.path("choices");

        if (choices.isArray() && !choices.isEmpty()) {
            return choices.get(0).path("message").path("content").asText();
        }

        throw new IllegalStateException("Unexpected response structure from Groq API: " + rawResponse);
    }
}

