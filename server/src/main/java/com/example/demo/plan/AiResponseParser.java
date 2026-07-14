package com.example.demo.plan;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class AiResponseParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode parse(String raw) {
        String text = raw.trim();
        text = text.replaceAll("```json\\s*", "").replaceAll("```", "").trim();

        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            text = text.substring(start, end + 1);
        }

        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            ObjectNode fallback = mapper.createObjectNode();
            fallback.put("type", "question");
            fallback.put("message", raw.trim());
            return fallback;
        }
    }
}
