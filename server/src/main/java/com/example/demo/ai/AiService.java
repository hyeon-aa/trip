package com.example.demo.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.demo.plan.dto.ChatMessageDto;
import com.example.demo.redis.RedisService;

@Service
public class AiService {

    private final RedisService redisService;

    public AiService(RedisService redisService) {
        this.redisService = redisService;
    }

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
        String key = cacheKey(text);
        String cached = redisService.get(key);
        if (cached != null) {
            System.out.println("[embedding cache] hit: " + key);
            return cached;
        }
        System.out.println("[embedding cache] miss: " + key);

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

        String result = response.embedding.values.toString();
        redisService.save(key, result);
        return result;
    }

    // 대화 텍스트를 그대로 키로 쓰지 않고 SHA-256으로 해시해 짧고 고정된
    // 길이의 캐시 키를 만든다. 같은 입력은 항상 같은 키가 나온다.
    String cacheKey(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "embedding:" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String chatWithGemini(List<ChatMessageDto> messages) {
        RestClient restClient = RestClient.create();
    
        List<Map<String, Object>> contents = messages.stream()
            .filter(m -> !m.role().equals("system"))
            .map(m -> Map.<String, Object>of(
                "role", m.role().equals("assistant") ? "model" : "user",
                "parts", List.of(Map.of("text", m.content()))
            ))
            .toList();
    
        String systemInstruction = messages.stream()
            .filter(m -> m.role().equals("system"))
            .findFirst()
            .map(ChatMessageDto::content)
            .orElse("");
    
        Map<String, Object> body = Map.of(
            "system_instruction", Map.of("parts", List.of(Map.of("text", systemInstruction))),
            "contents", contents
        );
    
        GeminiChatResponse response = restClient.post()
            .uri("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey)
            .header("Content-Type", "application/json")
            .body(body)
            .retrieve()
            .body(GeminiChatResponse.class);
    
        return response.candidates.get(0).content.parts.get(0).text;
    }
}
