package com.example.demo.plan;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.ai.AiService;
import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.wishlist.Wishlist;
import com.example.demo.wishlist.WishlistRepository;

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
    public SseEmitter chat(@RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter();

        String message = (String) body.get("message");
        List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");

        // 위시리스트 컨텍스트
        List<Wishlist> wishlist = wishlistRepository.findAll();
        String wishlistStr = wishlist.stream()
            .map(w -> "- " + w.getName() + " (" + w.getCategory() + ")")
            .reduce("", (a, b) -> a + "\n" + b);

        // RAG - 유사 장소 검색
        String queryEmbedding = aiService.createEmbedding(message);
        List<JejuPlace> relatedPlaces = jejuPlaceRepository.findSimilarPlaces(queryEmbedding, 10);
        String placesStr = relatedPlaces.stream()
            .map(p -> "- " + p.getName() + " (" + p.getCategory() + ") " + p.getAddress())
            .reduce("", (a, b) -> a + "\n" + b);

            String systemPrompt = String.format("""
                당신은 수천 명의 여행 일정을 설계해 온 전문 여행 플래너입니다.
                
                사용자의 위시리스트는 사용자의 취향과 관심사를 파악하기 위한 참고 자료입니다.
                
                [추천 가능한 실제 제주도 장소]
                %s
                
                [사용자 위시리스트]
                %s
                
                반드시 아래 JSON 형식으로 응답하세요.

                {
                "message": "친근한 설명",
                "schedule": {
                    "days": [
                    {
                        "day": 1,
                        "places": [
                        {
                            "name": "장소명",
                            "category": "관광지 | 맛집 | 카페",
                            "reason": "이 장소를 추천한 이유",
                            "recommendedTime": "추천 방문 시간"
                        }
                        ]
                    }
                    ]
                }
                }

                규칙:
                1. 하루 일정은 최소 4개 장소로 구성하세요.
                2. 관광지 2곳 이상 포함하세요.
                3. 맛집 1곳 이상 반드시 포함하세요.
                4. 카페 1곳 이상 반드시 포함하세요.
                5. 각 장소마다 추천 이유를 작성하세요.
                6. 실제 존재하는 장소명을 사용하세요.
                7. 카카오맵에서 검색 가능한 공식 명칭을 사용하세요.
                8. 추천 방문 시간을 작성하세요.
                9. 이동 동선을 고려하여 일정을 구성하세요.
                10. JSON 이외의 텍스트는 절대 출력하지 마세요.
                11. 모든 텍스트는 반드시 한국어로만 작성하세요. 
                12. 영어, 일본어, 중국어 등 다른 언어는 절대 사용하지 마세요. 
                """, placesStr, wishlistStr);

        // 히스토리 구성
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        if (history != null) messages.addAll(history);
        messages.add(Map.of("role", "user", "content", message));

        // 별도 스레드에서 AI 호출
        new Thread(() -> {
            try {
                String response = aiService.chat(messages);
                emitter.send(SseEmitter.event().data(response));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }
}