package com.ltim.poc.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.util.function.Function;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.io.IOException;

@Configuration
public class AIConfig {

    // Record for path-based requests
    public record PathRequest(String path) {}

    // Record for writing files
    public record WriteRequest(String path, String content) {}

    @Bean
    @Description("Lists all files in a specific directory path")
    public Function<PathRequest, List<String>> listFiles() {
        return request -> {
            try (var stream = Files.walk(Paths.get(request.path()), 1)) {
                return stream.filter(Files::isRegularFile)
                        .map(p -> p.getFileName().toString())
                        .toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Bean
    @Description("Reads the text content of a file, truncated if too large")
    public Function<PathRequest, String> readFile() {
        return request -> {
            try {
                String content = Files.readString(Paths.get(request.path()));
                // 🔑 Truncate to ~15,000 characters (~4k tokens) to ensure room for the prompt
                if (content.length() > 15000) {
                    return content.substring(0, 15000) + "\n... [TRUNCATED DUE TO SIZE]";
                }
                return content;
            } catch (IOException e) {
                return "Error: " + e.getMessage();
            }
        };
    }
    @Bean
    @Description("Writes content to a specific file path")
    public Function<WriteRequest, String> writeFile() {
        return request -> {
            try {
                var filePath = Paths.get(request.path());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, request.content());
                return "File written successfully";
            } catch (IOException e) {
                return "Error writing file: " + e.getMessage();
            }
        };
    }


}