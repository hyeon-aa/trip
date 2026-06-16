package com.example.demo.jeju;

import org.springframework.stereotype.Service;

import com.example.demo.ai.AiService;
import com.example.demo.place.PlaceService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class JejuInitService {

    private final JejuPlaceRepository jejuPlaceRepository;
    private final PlaceService placeService;
    private final AiService aiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JejuInitService(JejuPlaceRepository jejuPlaceRepository,
                           PlaceService placeService,
                           AiService aiService) {
        this.jejuPlaceRepository = jejuPlaceRepository;
        this.placeService = placeService;
        this.aiService = aiService;
    }

    public void initJejuPlaces() throws Exception {
        String[] keywords = { "제주도 맛집", "제주도 카페"};
        int count = 0; 

        for (String keyword : keywords) {
            String result = placeService.searchPlaces(keyword);
            JsonNode documents = objectMapper.readTree(result).get("documents");

            for (JsonNode doc : documents) {
                if (count >= 20) return;

                String name = doc.get("place_name").toString().replace("\"", "");
                String category = doc.get("category_name").toString().replace("\"", "");
                String address = doc.get("address_name").toString().replace("\"", "");
                double lat = doc.get("y").asDouble();
                double lng = doc.get("x").asDouble();

                // 이미 저장된 장소면 스킵
                boolean exists = jejuPlaceRepository.findAll().stream()
                    .anyMatch(p -> p.getName().equals(name));
                if (exists) continue;

                // 임베딩 텍스트 구성
                String textToEmbed = name + " " + category + " " + address;
                String embedding = aiService.createEmbedding(textToEmbed);

               jejuPlaceRepository.saveWithEmbedding(
                    name,
                    category,
                    address,
                    lat,
                    lng,
                    "",  // 맛집/카페는 description 없으니까 빈 문자열
                    embedding
                );

                System.out.println("저장 완료: " + name);
            }
        }
    }
}