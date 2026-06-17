package com.example.demo.ai;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.demo.plan.dto.ChatMessageDto;

@Service
public class AiService {

    @Value("${groq.api.key}")
    private String groqApiKey;


    public String chat(List<ChatMessageDto> messages) {
        RestClient restClient = RestClient.create();

        Map<String, Object> body = Map.of(
            "model", "llama-3.3-70b-versatile",
            "messages", messages
        );

        GroqResponse response = restClient.post()
            .uri("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer " + groqApiKey)
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body(GroqResponse.class);

        return response.choices.get(0).message.content;
    }

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public String createEmbedding(String text) {
        RestClient restClient = RestClient.create();
    
        Map<String, Object> body = Map.of(
            "content", Map.of(
                "parts", List.of(Map.of("text", text))
            )
        );
    
        GeminiEmbeddingResponse response = restClient.post()
            .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=" + geminiApiKey)
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body(GeminiEmbeddingResponse.class);
    
        return response.embedding.values.toString();
    }
}
