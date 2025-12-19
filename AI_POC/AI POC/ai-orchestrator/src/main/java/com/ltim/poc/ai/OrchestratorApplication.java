
package com.ltim.poc.ai;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Primary;
@SpringBootApplication(exclude = {
        org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration.class
})
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }

    @Bean
    @Primary
    public OpenAiChatModel chatModel(@Value("${spring.ai.openai.api-key}") String apiKey,
                                     RestClient.Builder restClientBuilder,
                                     WebClient.Builder webClientBuilder,
                                     ApplicationContext applicationContext) {

        var openAiApi = new OpenAiApi(
                "https://models.inference.ai.azure.com",
                apiKey,
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        // 🔑 CONFIGURE RETRY: Wait 5 seconds, then 10, then 20 when hitting 429
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(4)
                .exponentialBackoff(5000, 2.0, 20000)
                .retryOn(HttpClientErrorException.TooManyRequests.class)
                .build();

        FunctionCallbackContext context = new FunctionCallbackContext();
        context.setApplicationContext(applicationContext);

        return new OpenAiChatModel(
                openAiApi,
                OpenAiChatOptions.builder().withModel("gpt-4o").build(),
                context,
                retryTemplate
        );
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, VectorStore vectorStore) {
        return builder
                .defaultAdvisors(
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
                                // 🔑 Limit to top 3-5 most relevant snippets to save tokens
                                .withTopK(4)
                                // 🔑 Only include snippets with a similarity score > 0.7
                                .withSimilarityThreshold(0.7))
                )
                .build();
    }

    @Bean
    public ChatClient architectAgent(ChatModel chatModel, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()
                                // 🔑 Drop TopK to 2. This is the most effective way to stop the 413 error.
                                .withTopK(2)
                                // 🔑 Only high-quality matches.
                                .withSimilarityThreshold(0.8))
                )
                .defaultSystem("You are a Lead Architect. Be concise. Analyze only provided snippets.")
                .defaultFunctions("listFiles", "readFile")
                .build();
    }
    @Bean
    public ChatClient engineerAgent(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("You are a Senior Engineer. Use writeFile to save results.")
                .defaultFunctions("writeFile")
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(@Value("${spring.ai.openai.api-key}") String apiKey,
                                         RestClient.Builder restClientBuilder,
                                         WebClient.Builder webClientBuilder) {
        var openAiApi = new OpenAiApi(
                "https://models.inference.ai.azure.com",
                apiKey,
                "/chat/completions",
                "/embeddings",
                restClientBuilder,
                webClientBuilder,
                new DefaultResponseErrorHandler()
        );

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().withModel("text-embedding-3-small").build());
    }

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return new SimpleVectorStore(embeddingModel);
    }


}