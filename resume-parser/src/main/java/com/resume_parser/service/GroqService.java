package com.resume_parser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${groq.base.url}")
    private String groqBaseUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    public GroqService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> verifyApiKeyStatus() {
        if (groqApiKey == null || groqApiKey.isBlank()) {
            return Map.of(
                    "apiKeyConfigured", false,
                    "status", "NOT_CONFIGURED",
                    "message", "GROQ_API_KEY is not configured in backend environment."
            );
        }

        try {
            Map<String, Object> testRequest = Map.of(
                    "model", "llama-3.3-70b-versatile",
                    "messages", List.of(Map.of("role", "user", "content", "ping")),
                    "max_tokens", 5
            );

            String response = webClient.post()
                    .uri(groqBaseUrl)
                    .bodyValue(testRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response != null && (response.contains("choices") || response.contains("llama") || response.contains("id"))) {
                return Map.of(
                        "apiKeyConfigured", true,
                        "status", "CONNECTED",
                        "model", "llama-3.3-70b-versatile",
                        "message", "AI service is connected successfully."
                );
            }

            return Map.of(
                    "apiKeyConfigured", true,
                    "status", "UNEXPECTED_RESPONSE",
                    "message", "AI service returned an unexpected response."
            );
        } catch (Exception e) {
            log.warn("AI service connectivity test failed: {}", e.getMessage());
            return Map.of(
                    "apiKeyConfigured", true,
                    "status", "ERROR",
                    "message", "Failed to connect to AI service. Please check network connectivity or key validity."
            );
        }
    }

    public String getAtsScore(String resumeText, String jdText) throws Exception {
        String systemRole = """
                You are an expert ATS (Applicant Tracking System) evaluator.
                Your task is to accurately analyze a Resume against a Job Description (JD) and output a comprehensive JSON evaluation.

                CRITICAL RULES:
                1. Identify all mandatory/core requirements from the JD and list them in "jd_required_skills".
                2. Identify all preferred, nice-to-have, bonus, or secondary skills from the JD and list them in "jd_preferred_skills".
                3. For every skill in "jd_required_skills":
                   - If found in the candidate's resume -> add to "matched_required".
                   - If NOT found in the candidate's resume -> add to "missing_required".
                   (Every required skill MUST be accounted for in either matched_required or missing_required).
                4. For every skill in "jd_preferred_skills":
                   - If found in the candidate's resume -> add to "matched_preferred".
                   - If NOT found in the candidate's resume -> add to "missing_preferred".
                   (Every preferred skill MUST be accounted for in either matched_preferred or missing_preferred. If a preferred skill is missing from the resume, it MUST be in missing_preferred).
                5. Extract all candidate skills found in the resume into "resume_skills".
                6. Extract required experience string (e.g. "3-5 Years") into "required_experience" and numeric value into "jd_experience_years". If not mentioned in JD, return "0 Years" (and 0 for numeric).
                7. Extract candidate total experience string (e.g. "4 Years") into "resume_experience". If not mentioned in Resume, return "0 Years".
                8. Calculate a fair "ats_score" (0-100) based on the match percentage of required and preferred skills.
                9. Return ONLY a single, valid JSON object without markdown formatting.
                """;

        String userPromptTemplate = """
                Analyze the following resume against the job description.

                Job Description:
                %s

                Resume:
                %s

                Return a single JSON object with this exact structure:
                {
                  "jd_required_skills": ["Java", "Spring Boot"],
                  "jd_preferred_skills": ["AWS", "Docker"],
                  "jd_experience_years": 3,
                  "required_experience": "3+ Years",
                  "resume_experience": "4 Years",
                  "resume_skills": ["Java", "Spring Boot", "SQL"],
                  "matched_required": ["Java", "Spring Boot"],
                  "matched_preferred": [],
                  "missing_required": [],
                  "missing_preferred": ["AWS", "Docker"],
                  "ats_score": 85
                }
                """;

        String userPrompt = userPromptTemplate
                .replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(jdText))
                .replaceFirst("%s", java.util.regex.Matcher.quoteReplacement(resumeText));

        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemRole),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            log.info("Sending ATS evaluation request to Groq API using model llama-3.3-70b-versatile...");

            String rawResponse = webClient.post()
                    .uri(groqBaseUrl)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(15))
                    .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new IllegalStateException("Received empty response from Groq API.");
            }

            // Detect if network firewall (e.g. CUHP Web Filter) returned an HTML block page
            if (rawResponse.trim().startsWith("<") || rawResponse.contains("<title>Web Page Blocked</title>")) {
                log.warn("Groq API endpoint was blocked by network firewall (AI-platform-service policy). Engaging local ATS evaluation engine.");
                return evaluateLocally(resumeText, jdText);
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");

            if (choices.isArray() && !choices.isEmpty()) {
                String content = choices.get(0).path("message").path("content").asText();
                if (content != null) {
                    content = content.trim();
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    } else if (content.startsWith("```")) {
                        content = content.substring(3);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }
                    content = content.trim();

                    // Validate that it's valid JSON
                    objectMapper.readTree(content);
                    return content;
                }
            }

            throw new IllegalStateException("Unexpected response structure from Groq API: " + rawResponse);
        } catch (Exception e) {
            log.warn("Groq API call failed (Network/Firewall Block/SSL): {}. Falling back to local ATS analysis engine.", e.getMessage());
            return evaluateLocally(resumeText, jdText);
        }
    }

    /**
     * Local ATS Evaluation fallback when external LLM API is blocked by network firewalls.
     */
    private String evaluateLocally(String resumeText, String jdText) throws Exception {
        String[] commonSkills = {
                "Java", "Spring Boot", "Spring", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue", "Next.js", "Node.js",
                "SQL", "MySQL", "PostgreSQL", "Oracle", "MongoDB", "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Git", "GitHub", "REST",
                "Microservices", "HTML", "CSS", "C++", "C#", ".NET", "Linux", "CI/CD", "Redis",
                "Kafka", "GraphQL", "Agile", "Scrum", "API", "Unit Testing", "Hibernate", "JPA",
                "Cloud Services", "Machine Learning", "AI", "DevOps", "Cybersecurity", "Terraform", "Tailwind"
        };

        String lowerResume = resumeText.toLowerCase();
        String lowerJd = jdText.toLowerCase();

        // Dynamically detect experience in JD if mentioned (e.g., "5+ years", "3 years")
        int jdExperienceYears = 0;
        String requiredExperienceText = "0 Years";
        java.util.regex.Matcher expMatcher = java.util.regex.Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(jdText);
        if (expMatcher.find()) {
            try {
                jdExperienceYears = Integer.parseInt(expMatcher.group(1));
                requiredExperienceText = expMatcher.group(1) + "+ Years";
            } catch (NumberFormatException ignored) {}
        }

        // Dynamically detect experience in resume if mentioned (e.g. "3+ years", "5 years")
        String resumeExperienceText = "0 Years";
        java.util.regex.Matcher resumeExpMatcher = java.util.regex.Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)(?:\\s*of)?\\s*(?:experience|exp)?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(resumeText);
        if (resumeExpMatcher.find()) {
            resumeExperienceText = resumeExpMatcher.group(1) + "+ Years";
        }

        // Split JD into required and preferred text segments if explicit keywords exist
        String requiredJdPart = lowerJd;
        String preferredJdPart = "";

        int prefIdx = -1;
        String[] prefKeywords = {"preferred", "nice to have", "good to have", "bonus", "plus", "optional", "desired"};
        for (String kw : prefKeywords) {
            int idx = lowerJd.indexOf(kw);
            if (idx != -1 && (prefIdx == -1 || idx < prefIdx)) {
                prefIdx = idx;
            }
        }

        if (prefIdx != -1) {
            requiredJdPart = lowerJd.substring(0, prefIdx);
            preferredJdPart = lowerJd.substring(prefIdx);
        }

        List<String> jdRequired = new java.util.ArrayList<>();
        List<String> jdPreferred = new java.util.ArrayList<>();
        List<String> resumeSkills = new java.util.ArrayList<>();
        List<String> matchedRequired = new java.util.ArrayList<>();
        List<String> missingRequired = new java.util.ArrayList<>();
        List<String> matchedPreferred = new java.util.ArrayList<>();
        List<String> missingPreferred = new java.util.ArrayList<>();

        // Detect all candidate skills from resume
        for (String skill : commonSkills) {
            String skillLower = skill.toLowerCase();
            if (lowerResume.matches(".*\\b" + java.util.regex.Pattern.quote(skillLower) + "\\b.*") || lowerResume.contains(skillLower)) {
                if (!resumeSkills.contains(skill)) {
                    resumeSkills.add(skill);
                }
            }
        }

        // Scan for catalog skills in JD
        for (String skill : commonSkills) {
            String skillLower = skill.toLowerCase();
            boolean inRequiredPart = requiredJdPart.contains(skillLower);
            boolean inPreferredPart = preferredJdPart.contains(skillLower);
            boolean inResume = lowerResume.contains(skillLower);

            // Avoid redundant shorter substrings (e.g. "Spring" vs "Spring Boot")
            if (skill.equals("Spring") && (requiredJdPart.contains("spring boot") || preferredJdPart.contains("spring boot"))) {
                continue;
            }
            if (skill.equals("API") && (requiredJdPart.contains("rest") || preferredJdPart.contains("rest"))) {
                continue;
            }

            if (inPreferredPart) {
                if (!jdPreferred.contains(skill)) {
                    jdPreferred.add(skill);
                    if (inResume) {
                        matchedPreferred.add(skill);
                    } else {
                        missingPreferred.add(skill);
                    }
                }
            } else if (inRequiredPart) {
                if (!jdRequired.contains(skill)) {
                    jdRequired.add(skill);
                    if (inResume) {
                        matchedRequired.add(skill);
                    } else {
                        missingRequired.add(skill);
                    }
                }
            }
        }

        // Extract custom phrases / tokens from preferred part if any
        if (!preferredJdPart.isEmpty()) {
            String[] tokens = preferredJdPart.split("[,;\\n•]+");
            for (String token : tokens) {
                String clean = token.replaceAll("[^a-zA-Z0-9#+.\\s]", "").trim();
                if (clean.length() >= 2 && clean.length() <= 25 && !jdPreferred.contains(clean) && !jdRequired.contains(clean)) {
                    // check if it's a valid skill phrase
                    if (!clean.equalsIgnoreCase("preferred") && !clean.equalsIgnoreCase("nice to have") && !clean.equalsIgnoreCase("plus")) {
                        jdPreferred.add(clean);
                        if (lowerResume.contains(clean.toLowerCase())) {
                            matchedPreferred.add(clean);
                        } else {
                            missingPreferred.add(clean);
                        }
                    }
                }
            }
        }

        // If no preferred section was explicitly marked, and there are many required skills, allocate secondary ones to preferred
        if (jdPreferred.isEmpty() && jdRequired.size() > 4) {
            List<String> reallocated = new java.util.ArrayList<>(jdRequired.subList(4, jdRequired.size()));
            jdRequired = new java.util.ArrayList<>(jdRequired.subList(0, 4));

            matchedRequired.removeIf(reallocated::contains);
            missingRequired.removeIf(reallocated::contains);

            for (String skill : reallocated) {
                jdPreferred.add(skill);
                if (lowerResume.contains(skill.toLowerCase())) {
                    matchedPreferred.add(skill);
                } else {
                    missingPreferred.add(skill);
                }
            }
        }

        int totalSkills = jdRequired.size() + jdPreferred.size();
        int totalMatched = matchedRequired.size() + matchedPreferred.size();
        int atsScore = totalSkills > 0 ? (int) Math.round(((double) totalMatched / totalSkills) * 100) : 0;

        Map<String, Object> fallbackResult = new java.util.LinkedHashMap<>();
        fallbackResult.put("jd_required_skills", jdRequired);
        fallbackResult.put("jd_preferred_skills", jdPreferred);
        fallbackResult.put("jd_experience_years", jdExperienceYears);
        fallbackResult.put("required_experience", requiredExperienceText);
        fallbackResult.put("resume_experience", resumeExperienceText);
        fallbackResult.put("resume_skills", resumeSkills);
        fallbackResult.put("matched_required", matchedRequired);
        fallbackResult.put("matched_preferred", matchedPreferred);
        fallbackResult.put("missing_required", missingRequired);
        fallbackResult.put("missing_preferred", missingPreferred);
        fallbackResult.put("ats_score", atsScore);

        return objectMapper.writeValueAsString(fallbackResult);
    }
}

