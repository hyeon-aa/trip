package com.example.demo.jeju;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.example.demo.ai.AiService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class VisitJejuService {

    @Value("${visitjeju.api.key}")
    private String apiKey;

    private final JejuPlaceRepository jejuPlaceRepository;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VisitJejuService(JejuPlaceRepository jejuPlaceRepository, AiService aiService) {
        this.jejuPlaceRepository = jejuPlaceRepository;
        this.aiService = aiService;
    }

    public void initFromVisitJeju() throws Exception {
        RestClient restClient = RestClient.create();

        for (int page = 1; page <= 10; page++) {
            String response = restClient.get()
                .uri("http://api.visitjeju.net/vsjApi/contents/searchList?apiKey=" + apiKey + "&locale=kr&page=" + page)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items == null || items.isEmpty()) break;

            for (JsonNode item : items) {
                if (item.has("contentscd")) {
                    String contentType = item.get("contentscd").get("value").toString().replace("\"", "");
                    if (!contentType.equals("c1")) continue;
                }

                String name = item.get("title").toString().replace("\"", "");
                String introduction = item.has("introduction") ? item.get("introduction").toString().replace("\"", "") : "";
                String address = item.has("address") ? item.get("address").toString().replace("\"", "") : "";
                String tag = item.has("tag") ? item.get("tag").toString().replace("\"", "") : "";
                double lat = item.has("latitude") ? item.get("latitude").asDouble() : 0;
                double lng = item.has("longitude") ? item.get("longitude").asDouble() : 0;
            
                // 지역 정보 파싱
                String region1 = "";
                String region2 = "";
                if (item.has("region1cd") && item.get("region1cd").has("label")) {
                    region1 = item.get("region1cd").get("label").toString().replace("\"", "");
                }
                if (item.has("region2cd") && item.get("region2cd").has("label")) {
                    region2 = item.get("region2cd").get("label").toString().replace("\"", "");
                }
            
                boolean exists = jejuPlaceRepository.findAll().stream()
                    .anyMatch(p -> p.getName().equals(name));
                if (exists) continue;
            
                // 임베딩 텍스트 - 특성 + 거리 다 포함
                String textToEmbed = String.format("%s %s %s %s %s",
                    name, introduction, tag, region1, region2);
            
                String embedding = aiService.createEmbedding(textToEmbed);
            
                jejuPlaceRepository.saveWithEmbedding(
                    name,
                    "관광지",
                    address,
                    lat,
                    lng,
                    introduction,
                    embedding
                );
                System.out.println("저장 완료: " + name);
            }
        }
    }
}