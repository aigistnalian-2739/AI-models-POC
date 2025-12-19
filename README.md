# Agentic Code Orchestrator

An automated software development suite built with **Spring AI** and **GitHub Models**. It leverages high-reasoning agents to perform complex architecture analysis, generate documentation, and conduct quality audits on local codebases.

## 🎯 Project Overview

This project intends to automate the critical but manual parts of the SDLC. By providing an absolute path to a Java project, you can delegate tasks to an "Agent Trinity" (Architect, Engineer, Auditor) to scan code and generate high-fidelity artifacts.

### Core Features

* **Recursive Project Analysis**: Scans all directories to build a full context map of your application.
* **OpenAPI 3.1 Generation**: Produces detailed YAML specs including request/response schemas, error codes (400, 404, 500), and realistic mock test data.
* **Rule-Based Auditing**: Ingests custom Checkstyle or corporate rulesets (via file upload) to flag non-compliant code.
* **Local RAG & Memory**: Uses an embedded **H2 database** and **Advisors API** to remember project context and provide faster, semantically aware responses.

---

## 🚀 Implementation & Running

### Prerequisites

* **Java 17+**
* **Spring Boot 3.4.1**
* **GitHub Personal Access Token** with `models:read` scope.

### 1. Configuration

Set your GitHub token as an environment variable to keep it secure:

```bash
export GITHUB_TOKEN=your_github_pat_here

```

Your `application.yml` should point to the GitHub inference endpoint:

```yaml
spring:
  ai:
    openai:
      base-url: https://models.inference.ai.azure.com
      chat:
        completions-path: /chat/completions
        options:
          model: gpt-4o # or gpt-5.2 if available

```

### 2. Run Instructions

1. **Build**: `mvn clean install`
2. **Start**: Run `OrchestratorApplication.java`.
3. **Use**: Open `http://localhost:8080` in your browser.
4. **Execute**: Enter the **Absolute Path** of your target project, select a **Mission Mode** from the dropdown, and click **Run Agent**.

---

## 📈 Improvement Roadmap

### Current Version (POC)

* [x] Native Function Calling for File I/O.
* [x] Multi-agent context passing.
* [x] Multipart file upload for rulesets.
* [x] Embedded H2 persistence.

### Future Enhancements

* **The Refactor Agent**: Implement a `patchFile` tool to automatically fix quality violations found during audits.
* **Semantic Caching**: Integrate Redis to cache common architectural queries, reducing LLM token costs.
* **CI/CD Integration**: A CLI mode to run audits and doc generation as part of a GitHub Action or Jenkins pipeline.
* **GPT-5 Reasoning**: Utilize `reasoning_effort` parameters for deeper logic analysis (e.g., detecting concurrency leaks).
* **GPT-5 Reasoning**: Utilize `reasoning_effort` parameters for deeper logic analysis (e.g., detecting concurrency leaks).
