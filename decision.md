# Architecture & Engineering Decision Record (ADR)

**Project:** Resume Parser & ATS Match Evaluator  
**Document:** DECISION.md  
**Date:** August 2026  
**Status:** Accepted & Implemented  

---

## 1. Executive Summary

This document details the architectural decisions, technical trade-offs, and root-cause fixes implemented across the **Spring Boot 3.4.2 backend** and **React 19 / TypeScript / Vite frontend**. It explains **why specific approaches were selected** over alternatives to build a resilient, production-ready, and fail-safe ATS evaluation system.

---

## 2. Key Technical Decisions & In-Depth Rationale

### 🟢 Decision 1: Dual-Mode ATS Evaluation (Cloud LLM + Local Heuristic Fallback Engine)

#### The Problem:
When calling external LLM providers (e.g. Groq Cloud API pi.groq.com), institutional and campus networks (such as CUHP firewall / Web Filters) intercept outbound HTTPS connections to domains categorized under AI-platform-service and return an HTML block page (<title>Web Page Blocked</title>). Attempting to parse this HTML document caused runtime JSON parsing exceptions and complete evaluation failures for users on restricted networks.

#### The Selected Approach:
We implemented an **automatic, resilient dual-mode evaluation pipeline** in GroqService.java:
1. **Primary Mode:** Calls Groq Cloud API using llama-3.3-70b-versatile with structured JSON enforcement (esponse_format: {type: json_object}).
2. **Fallback Mode:** If the connection is blocked by a firewall, returns HTML, or fails with network/SSL timeout, the service automatically engages an **embedded local ATS analyzer**.
3. **Local Analyzer Logic:** Computes lexical and semantic overlap between the extracted resume skills and the Target Job Description requirements, generating exact match scores, matched required skills, missing required skills, and preferred qualifications according to the ATS schema.

#### Why Only This Approach?
* **Zero Downtime Guarantee:** The application remains 100% functional and testable anywhere (campus Wi-Fi, restricted corporate networks, offline environments, or personal hotspots).
* **No External Dependencies Required:** Does not require paying for proxy services or modifying local network administrator security policies.
* **Transparent to Frontend:** Both Groq Cloud and the local analyzer return the exact same JSON contract (AtsResult), so the frontend UI renders identical dashboards seamlessly.

---

### 🟢 Decision 2: Groq Model Upgrade to llama-3.3-70b-versatile & Structured JSON Mode

#### The Problem:
The codebase initially hardcoded llama3-70b-8192. Groq decommissioned this model in late 2024, causing the API to return HTTP 400/404 model_decommissioned errors. Furthermore, prompt-only requests without JSON mode allowed the LLM to wrap responses in markdown codeblocks (`` `json { ... } ` ``), breaking JSON parsers.

#### The Selected Approach:
* Migrated to the current flagship Groq model: llama-3.3-70b-versatile.
* Configured response_format: {type: json_object} in the request payload.
* Added defensive markdown fence stripping in Java before returning the JSON payload.

#### Why This Approach?
* Guarantees strict JSON schema conformance without unexpected preamble or markdown formatting errors.
* Delivers faster inference speeds (over 250 tokens/sec on Groq LPUs) and higher accuracy in skill extraction.

---

### 🟢 Decision 3: API Contract & Parameter Harmonization

#### The Problem:
The frontend was sending multipart form data with the key 'resume' and targeting http://localhost:8000/api/v1/resumes/upload. The backend expected 'file' and 'jdText' at http://localhost:8081/parse/resume. This caused immediate HTTP 400 / 404 connection failures.

#### The Selected Approach:
* Standardized the unified endpoint: POST /parse/resume on port 8081.
* Form field keys aligned:
  - ile: The resume binary file (.pdf, .docx, .doc).
  - jdText: Target Job Description text (with intelligent default fallback if left empty).
* Added a Vite development proxy in ite.config.ts (/parse -> http://localhost:8081) to eliminate CORS or origin-port routing issues during local development.

---

### 🟢 Decision 4: Elimination of Silent Error Swallowing

#### The Problem:
pi.ts had a catch block that logged a console note and returned a synthetic { success: true, data: { message: Simulated success } } on every network or server failure. This hid actual bugs (CORS errors, 400 Bad Request, 500 crashes) and led developers to believe broken requests had succeeded.

#### The Selected Approach:
* Removed fake fallback responses in production API client methods.
* Added descriptive, human-readable error messages for connection failures (e.g. informing the developer if the Spring Boot server is not running on port 8081).
* Exposed actionable error states to the UI so users can retry or diagnose issues.

---

### 🟢 Decision 5: Browser Memory Management & Object URL Cleanup

#### The Problem:
Every selected file generated an in-memory blob URL via URL.createObjectURL(file). These URLs were never released, leading to cumulative memory leaks in long-running browser sessions.

#### The Selected Approach:
* Added explicit URL.revokeObjectURL(file.objectUrl) invocations inside handleRemoveFile, handleCancelFile, and component unmount useEffect hooks.
* Used useRef to safely track the latest files array without causing unwanted re-renders or stale closure captures.

---

### 🟢 Decision 6: Global Exception Handling & WebClient Thread Safety

#### The Problem:
Uncaught Spring MVC parameter exceptions or Groq WebClient errors (WebClientResponseException) defaulted to generic 500 errors with no structured JSON format. WebClient calls without timeouts risked hanging Spring MVC servlet threads indefinitely under network delays.

#### The Selected Approach:
* Added dedicated @ExceptionHandler methods in GlobalExceptionHandler.java for MissingServletRequestParameterException, MaxUploadSizeExceededException, HttpMediaTypeNotSupportedException, and WebClientResponseException.
* Configured ExchangeStrategies for 16MB in-memory buffer size in WebClientConfig.java.
* Bound all blocking WebClient calls to strict timeouts (Duration.ofSeconds(15)).

---

## 3. Configuration Reference

| Variable | Recommended Value | Purpose |
| :--- | :--- | :--- |
| VITE_API_BASE_URL | http://localhost:8081 | Spring Boot Backend Base URL |
| VITE_UPLOAD_ENDPOINT | /parse/resume | Resume & JD ATS Evaluation Endpoint |
| VITE_MAX_FILE_SIZE_MB | 10 | Aligned with Spring Boot Multipart limit |
| VITE_ALLOWED_FILE_TYPES| .pdf,.doc,.docx | Supported document formats |
| GROQ_BASE_URL | https://api.groq.com/openai/v1/chat/completions | Groq OpenAI-compatible endpoint |
| DECISION_DOC | decision.md | Path to this Architecture Decision Record |
