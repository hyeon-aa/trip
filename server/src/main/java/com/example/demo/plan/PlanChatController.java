package com.example.demo.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.ai.AiService;
import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.plan.dto.ChatMessageDto;
import com.example.demo.plan.dto.PlanChatRequest;
import com.example.demo.wishlist.Wishlist;
import com.example.demo.wishlist.WishlistRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@RestController
@RequestMapping("/plan")
public class PlanChatController {

    private final AiService aiService;
    private final WishlistRepository wishlistRepository;
    private final JejuPlaceRepository jejuPlaceRepository;

    public PlanChatController(AiService aiService, WishlistRepository wishlistRepository, JejuPlaceRepository jejuPlaceRepository) {
        this.aiService = aiService;
        this.wishlistRepository = wishlistRepository;
        this.jejuPlaceRepository = jejuPlaceRepository;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody PlanChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);

        String message = request.message();
        List<ChatMessageDto> history = request.history();

        // 위시리스트 - ID 부여
        List<Wishlist> wishlist = wishlistRepository.findAll();
        AtomicInteger wIdCounter = new AtomicInteger(1);
        Map<String, Wishlist> wishlistIdMap = wishlist.stream()
            .collect(Collectors.toMap(
                w -> "w" + wIdCounter.getAndIncrement(),
                w -> w,
                (a, b) -> a,
                java.util.LinkedHashMap::new
            ));

        String wishlistStr = wishlistIdMap.entrySet().stream()
            .map(e -> "[" + e.getKey() + "] " + e.getValue().getName() + " (" + e.getValue().getCategory() + ")")
            .collect(Collectors.joining("\n"));

        // RAG - 유사 장소 검색 - ID 부여
        String queryEmbedding = aiService.createEmbedding(message);
        List<JejuPlace> relatedPlaces = jejuPlaceRepository.findSimilarPlaces(queryEmbedding, 10);

        AtomicInteger pIdCounter = new AtomicInteger(1);
        Map<String, JejuPlace> placeIdMap = relatedPlaces.stream()
            .collect(Collectors.toMap(
                p -> "p" + pIdCounter.getAndIncrement(),
                p -> p,
                (a, b) -> a,
                java.util.LinkedHashMap::new
            ));

        String placesStr = placeIdMap.entrySet().stream()
            .map(e -> "[" + e.getKey() + "] " + e.getValue().getName() + " (" + e.getValue().getCategory() + ") " + e.getValue().getAddress())
            .collect(Collectors.joining("\n"));

        String systemPrompt = String.format("""
            당신은 수천 명의 여행 일정을 설계해 온 전문 여행 플래너입니다.

            [대화 진행 규칙]
            1. 다음 정보를 대화를 통해 순서대로 하나씩 파악하세요.
               - 여행 스타일 (자연/맛집/액티비티/휴양 등)
               - 동행자 (혼자/연인/가족/친구)
               - 여행 기간 (몇 박 며칠)
            2. 사용자가 이미 답한 정보는 절대 다시 묻지 마세요.
            3. 사용자가 말하지 않은 내용을 추측하거나 단정짓지 마세요. 모르면 그냥 물어보세요.
            4. 한 번에 한 가지 질문만, 자연스럽고 친근한 말투로 하세요.
            5. 위 3가지 정보가 모두 파악되면 그때 전체 일정을 생성하세요.
            6. 이미 일정을 만든 후 유저가 수정을 요청하면 기존 맥락을 유지하면서 일정을 수정하세요.

            [추천 가능한 실제 제주도 장소 - 반드시 아래 id 중에서만 선택]
            %s

            [사용자 위시리스트 - 반드시 아래 id 중에서만 선택]
            %s

            반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 출력하지 마세요.

            질문 단계일 때:
            {
              "type": "question",
              "message": "사용자에게 묻는 질문"
            }

            일정 생성/수정 단계일 때:
            {
              "type": "schedule",
              "message": "친근한 설명",
              "schedule": {
                "days": [
                  {
                    "day": 1,
                    "places": [
                      {
                        "id": "p3",
                        "reason": "추천 이유",
                        "recommendedTime": "추천 방문 시간"
                      }
                    ]
                  }
                ]
              }
            }

            규칙:
            1. 하루 일정은 최소 4개 장소로 구성하세요.
            2. 관광지 2곳 이상, 맛집 1곳 이상, 카페 1곳 이상 포함하세요.
            3. "id"는 반드시 위 목록에 제시된 [p1], [p2]... 또는 [w1], [w2]... 중에서만 선택하세요.
            4. 목록에 없는 장소는 절대 만들어내지 마세요.
            5. 모든 텍스트는 반드시 한국어로만 작성하세요.
            """, placesStr, wishlistStr);

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(new ChatMessageDto("system", systemPrompt));
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(new ChatMessageDto("user", message));

        new Thread(() -> {
            try {
                String response = aiService.chatWithGemini(messages);

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(response);
                String type = root.has("type") ? root.get("type").asText() : "";

                if (type.equals("schedule") && root.has("schedule")) {
                    ObjectNode scheduleNode = (ObjectNode) root.get("schedule");
                    ArrayNode days = (ArrayNode) scheduleNode.get("days");

                    for (JsonNode dayNode : days) {
                        ArrayNode places = (ArrayNode) dayNode.get("places");
                        for (JsonNode placeNode : places) {
                            ObjectNode placeObj = (ObjectNode) placeNode;
                            if (!placeObj.has("id")) continue;
                            String id = placeObj.get("id").asText();

                            if (placeIdMap.containsKey(id)) {
                                JejuPlace p = placeIdMap.get(id);
                                placeObj.put("name", p.getName());
                                placeObj.put("category", p.getCategory());
                                placeObj.put("lat", p.getLat());
                                placeObj.put("lng", p.getLng());
                            } else if (wishlistIdMap.containsKey(id)) {
                                Wishlist w = wishlistIdMap.get(id);
                                placeObj.put("name", w.getName());
                                placeObj.put("category", w.getCategory());
                                placeObj.put("lat", w.getLat());
                                placeObj.put("lng", w.getLng());
                            }
                        }
                    }
                }

                String finalResponse = mapper.writeValueAsString(root);
                emitter.send(SseEmitter.event().data(finalResponse));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}