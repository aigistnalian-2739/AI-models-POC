package com.ltim.poc.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document; // CORRECT IMPORT
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
public class CodeAnalystService {
    private final ChatClient architectAgent;
    private final ChatClient engineerAgent;
    private final ChatClient auditorAgent;
    private final VectorStore vectorStore;

    // Use a single constructor to inject all required beans
    public CodeAnalystService(ChatClient.Builder builder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;

        // Shared Advisor: This enables RAG for all agents
        // It will automatically retrieve context from the PgVectorStore (H2)
        var ragAdvisor = new QuestionAnswerAdvisor(vectorStore);

        // Build specialized agents using unique system prompts
        this.architectAgent = builder
                .defaultSystem("You are a Code Architect. Analyze architecture and REST endpoints.")
                .defaultAdvisors(ragAdvisor)
                .build();

        this.engineerAgent = builder
                .defaultSystem("You are a QA Engineer. Generate OpenAPI YAML specs and JUnit tests.")
                .defaultAdvisors(ragAdvisor)
                .build();

        this.auditorAgent = builder
                .defaultSystem("You are a Code Auditor. Find missing Javadocs and thread-safety risks.")
                .defaultAdvisors(ragAdvisor)
                .build();
    }
    public String runPipeline(String path) {
        // Phase 1: The Architect reads the project to build a context map
        String summary = architectAgent.prompt()
                .system("""
                You are a Lead Architect. Analyze the project structure and source code.
                Focus on identifying REST Controllers, Request/Response DTOs, and Error Handling logic.
                DO NOT suggest changes to the code.
                """)
                .user("Analyze project structure and controllers at: " + path)
                .functions("listFiles", "readFile")
                .call().content();

        // Phase 2: The Engineer generates the documentation as a standalone YAML file
        return engineerAgent.prompt()
                .system("""
                You are a Senior API Engineer. Your task is to generate a professional 'openapi.yaml' file.
                
                STRICT CONSTRAINTS:
                1. DO NOT rewrite or modify any existing source code files. Treat them as READ-ONLY.
                2. Use the 'writeFile' tool ONLY to create: {path}/generated/openapi.yaml.
                3. The YAML must be OpenAPI 3.1.0 compliant.
                
                DOCUMENTATION REQUIREMENTS:
                - Detailed Schemas: Every DTO found must be fully defined in 'components/schemas'.
                - Error Handing: Every endpoint MUST include 400 (Bad Request), 404 (Not Found), and 500 (Server Error) responses.
                - Test Requests: Include 'example' and 'examples' for all request bodies and parameters.
                - Formats: Specify data formats (e.g., uuid, date-time, int64) accurately.
                """.replace("{path}", path))
                .user("Based on this summary: " + summary + ". Create the detailed openapi.yaml in the /generated folder.")
                .functions("writeFile")
                .call().content();
    }
    public String runAudit(String path) {
        return auditorAgent.prompt()
                .user("Audit the code quality at: " + path)
                .functions("listFiles", "readFile")
                .call().content();
    }

    public String runFullPipeline(String path) {
        // Stage 0: Index the project (Local RAG)
        // This populates the SimpleVectorStore with the project's code
        ingestProjectFiles(path);

        // Phase 1: Context Gathering (Architect)
        // The Architect uses 'listFiles' and 'readFile' beans to explore the code
        String summary = architectAgent.prompt()
                .system("""
                                    You are a Lead Architect.
                                        IMPORTANT: You have a strict limit of 3 tool calls total.
                                        - listFiles once.
                                        - readFile ONLY for the most important Controller and its main DTO.
                                        Do not browse the whole project.
                        """)
                .user("Extract API metadata from project at: " + path)
                .functions("listFiles", "readFile") // Must match @Bean names in AIConfig
                .call().content();

        // 🔑 THROTTLE: Wait 10-15 seconds to avoid hitting the 15-req/min limit
        try {
            System.out.println("Wait for rate limit cool-down...");
            Thread.sleep(20000);
        } catch (InterruptedException ignored) {}

        // Phase 2: High-Fidelity Artifact Generation (Engineer)
        // The Engineer uses the Architect's summary and the 'writeFile' bean
        return engineerAgent.prompt()
                .system("""
            You are a Senior API Engineer. Your task is to generate a 'comprehensive_openapi.yaml'.
            
            STRICT OUTPUT RULES:
            1. DO NOT modify any existing files. Treat source code as READ-ONLY.
            2. Use 'writeFile' to save ONLY to: {path}/generated/openapi.yaml.
            
            METADATA REQUIREMENTS:
            - Provide 200 OK, 400, 404, and 500 responses.
            - Include 'format' and 'example' for all fields.
            """.replace("{path}", path))
                .user("Based on this Architect Summary: " + summary + ". Generate the detailed OpenAPI YAML.")
                .functions("writeFile") // Must match @Bean name in AIConfig
                .call().content();
    }


    public void ingestProjectFiles(String path) {
        try {
            List<java.nio.file.Path> filePaths = Files.walk(Paths.get(path))
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            var splitter = new TokenTextSplitter(300, 30, 5, 10000, true);

            for (java.nio.file.Path filePath : filePaths) {
                String content = Files.readString(filePath);

                if (content.length() > 20000) continue;

                Document doc = new Document(content, Map.of("source", filePath.toString()));
                List<Document> chunks = splitter.apply(List.of(doc));

                // 🔑 THE FIX: Loop through chunks and throttle the Embedding calls
                for (Document chunk : chunks) {
                    vectorStore.accept(List.of(chunk));

                    // Print to console so you can see progress during your demo
                    System.out.println(">>> Ingested chunk for: " + filePath.getFileName());

                    // Wait 4 seconds to respect the 15 req/min rate limit
                    Thread.sleep(4000);
                }
            }
            System.out.println("SUCCESS: RAG Ingestion complete.");
        } catch (Exception e) {
            throw new RuntimeException("RAG Ingestion failed: " + e.getMessage(), e);
        }
    }
}