package com.example.demo.plan;

import java.util.ArrayList;
import java.util.Collections;
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

    private static final double AIRPORT_LAT = 33.5070282;
    private static final double AIRPORT_LNG = 126.4913137;

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
        ObjectMapper mapper = new ObjectMapper();

        String message = request.message();
        List<ChatMessageDto> history = request.history();

        // 1. 사용자 위시리스트 조회 및 맵 변환
        List<Wishlist> wishlist = wishlistRepository.findAll();
        AtomicInteger wIdCounter = new AtomicInteger(1);
        Map<String, Wishlist> wishlistIdMap = wishlist.stream()
            .collect(Collectors.toMap(
                w -> "w" + wIdCounter.getAndIncrement(), w -> w, (a, b) -> a, java.util.LinkedHashMap::new));

        String wishlistStr = wishlistIdMap.entrySet().stream()
            .map(e -> "[" + e.getKey() + "] " + e.getValue().getName() + " (" + e.getValue().getCategory() + ")")
            .collect(Collectors.joining("\n"));

        // 2. 대화 기록을 하나로 합쳐 현재 문맥 파악
        String conversationText =
        history == null
            ? message
            : history.stream()
                .map(ChatMessageDto::content)
                .collect(Collectors.joining(" "))
                + " " + message;
        
        // 3. [핵심 추가] 사용자의 대화 흐름이나 수정 요청에서 특정 조건(지역, 카테고리) 낚아채기
        String targetRegion = null;
        if (conversationText.contains("서부")) targetRegion = "서부";
        else if (conversationText.contains("동부")) targetRegion = "동부";
        else if (conversationText.contains("남부")) targetRegion = "남부";
        else if (conversationText.contains("제주시")) targetRegion = "제주시";

        boolean wantsFood = conversationText.contains("맛집") || conversationText.contains("흑돼지") || conversationText.contains("식당") || conversationText.contains("갈치") || conversationText.contains("카페") || conversationText.contains("커피") || conversationText.contains("베이커리");
        boolean wantsNature = conversationText.contains("관광지") || conversationText.contains("자연") || conversationText.contains("바다") || conversationText.contains("숲");
        boolean wantsCulture = conversationText.contains("문화시설") || conversationText.contains("박물관") || conversationText.contains("미술관") || conversationText.contains("전시");
        boolean wantsSports = conversationText.contains("레포츠") || conversationText.contains("액티비티") || conversationText.contains("서핑") || conversationText.contains("스쿠버") || conversationText.contains("승마");

        int mentionedCount = (wantsFood ? 1 : 0) + (wantsNature ? 1 : 0) + (wantsCulture ? 1 : 0) + (wantsSports ? 1 : 0);

        String targetCategory = null;
        if (mentionedCount == 1) {
            if (wantsFood) targetCategory = "음식점";
            else if (wantsNature) targetCategory = "관광지";
            else if (wantsCulture) targetCategory = "문화시설";
            else if (wantsSports) targetCategory = "레포츠";
        }
        // 여러 카테고리 동시 언급 시 null 유지 → 필터 없이 임베딩 유사도로 혼합 검색

        // 4. 질문 기반 임베딩 생성 및 하이브리드 필터링 검색 수행
        String queryEmbedding = aiService.createEmbedding(conversationText);
        
        // 새로 개편한 레포지토리의 findSimilarPlacesWithFilter 메서드 호출
        List<JejuPlace> relatedPlaces = jejuPlaceRepository.findSimilarPlacesWithFilter(
            queryEmbedding,
            targetRegion,   // 파싱된 지역 조건 (없으면 null 전달되어 무시됨)
            targetCategory, // 파싱된 카테고리 조건 (없으면 null 전달되어 무시됨)
            50              // AI에게 줄 추천 후보 풀 개수 (넉넉하게 50개)
        );

        // 만약 조건 필터링 조건이 너무 엄격해서 결과가 없으면, 전체에서 검색하도록 안전장치 작동
        if (relatedPlaces.isEmpty()) {
            relatedPlaces = jejuPlaceRepository.findSimilarPlacesWithFilter(queryEmbedding, null, null, 50);
        }

        // 로그 출력 (디버깅용)
        relatedPlaces.forEach(p ->
            System.out.println(
                "[" + p.getRegion() + "] " + p.getName() + " | " + p.getCategory()
            )
        );

        // 5. 추천 장소 리스트 ID 매핑 및 권역별 프롬프트 빌드
        AtomicInteger pIdCounter = new AtomicInteger(1);
        Map<String, JejuPlace> placeIdMap = relatedPlaces.stream()
            .collect(Collectors.toMap(
                p -> "p" + pIdCounter.getAndIncrement(), p -> p, (a, b) -> a, java.util.LinkedHashMap::new));

        Map<String, List<Map.Entry<String, JejuPlace>>> regionMap = placeIdMap.entrySet().stream()
            .collect(Collectors.groupingBy(e -> e.getValue().getRegion())); // 엔티티에 추가한 region 활용
            
        StringBuilder placesBuilder = new StringBuilder();
        for (String region : List.of("동부", "서부", "남부", "제주시")) {
            List<Map.Entry<String, JejuPlace>> regionPlaces = regionMap.get(region);
            if (regionPlaces == null || regionPlaces.isEmpty()) {
                continue;
            }
            placesBuilder.append("\n[").append(region).append("]\n");
            for (Map.Entry<String, JejuPlace> entry : regionPlaces) {
                JejuPlace p = entry.getValue();
                placesBuilder
                    .append("[")
                    .append(entry.getKey())
                    .append("] ")
                    .append(p.getName())
                    .append(" (")
                    .append(p.getCategory())
                    .append(")\n");
            }
        }
        String placesStr = placesBuilder.toString();

        // 6. 시스템 프롬프트 조립
        String systemPrompt = String.format("""
            당신은 수천 명의 여행 일정을 설계해 온 전문 여행 플래너입니다.

            [대화 진행 규칙]
            1. 다음 정보를 대화를 통해 순서대로 하나씩 파악하세요.
               - 여행 스타일 (자연/맛집/액티비티/휴양 등)
               - 동행자 (혼자/연인/가족/친구)
               - 여행 기간 (몇 박 며칠)
            2. 사용자가 이미 답한 정보는 절대 다시 묻지 마세요.
            3. 사용자가 말하지 않은 내용을 추측하거나 단정짓지 마세요.
            4. 사용자가 한 메시지에 여러 정보를 한꺼번에 알려주면 추가 질문 없이 바로 일정을 생성하세요.
            5. 부족한 정보만 한 번에 한 가지씩 자연스럽게 질문하세요.
            6. 위 3가지 정보가 모두 파악되면 전체 일정을 생성하세요.
            7. 이미 일정을 만든 후 유저가 수정을 요청하면 기존 맥락을 유지하면서 일정을 수정하세요.
            8. 첫째 날 출발 시간이나 마지막 날 비행기 시간을 사용자가 언급하면, 그 시간에 맞춰 일정을 조정하세요.
            9. 같은 날에는 반드시 같은 권역의 장소를 우선 사용하세요.
            10. 동부/서부/남부/제주시를 하루에 섞지 마세요.
            11. 이동시간 최소화를 최우선으로 고려하세요.
            12. 마지막 날은 공항 접근성을 고려하세요.
            13. 첫날 도착 시간이 있다면 그 시간 이전 일정은 배치하지 마세요.
            14. 마지막날 비행 시간이 있다면 공항 이동시간을 확보하세요.

            [추천 가능한 실제 제주도 장소 - 반드시 아래 id 중에서만 선택]
            %s

            [사용자 위시리스트 - 반드시 아래 id 중에서만 선택]
            %s

            반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트는 절대 출력하지 마세요.

            질문 단계일 때 (선택형 질문이면 options 포함):
            {
              "type": "question",
              "message": "질문 내용",
              "options": ["선택지1", "선택지2", "선택지3"]
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
                      { "id": "p3", "reason": "추천 이유", "recommendedTime": "추천 방문 시간" }
                    ]
                  }
                ]
              }
            }

            규칙:
            1. 장소 개수는 그날의 활동 강도에 따라 유동적으로 정하세요.
               - 등산(한라산 등), 장시간 액티비티가 포함된 날: 2~3곳
               - 가벼운 관광 위주인 날: 4~5곳
            2. 모든 날은 최소 2곳, 최대 5곳 이내로 구성하세요.
            3. "id"는 반드시 위 목록에 제시된 [p1], [p2]... 또는 [w1], [w2]... 중에서만 선택하세요.
            4. 목록에 없는 장소는 절대 만들어내지 마세요.
            5. 모든 텍스트는 반드시 한국어로만 작성하세요.
            """, placesStr, wishlistStr);

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(new ChatMessageDto("system", systemPrompt));
        if (history != null) messages.addAll(history);
        messages.add(new ChatMessageDto("user", message));

        // 7. 비동기 쓰레드로 AI 요청 및 데이터 후처리 가공 (SSE 스트리밍 전송)
        new Thread(() -> {
            try {
                String response = chatWithRetry(messages);
                JsonNode root = parseAiJson(response, mapper);
                String type = root.has("type") ? root.get("type").asText() : "";

                if (type.equals("schedule") && root.has("schedule")) {
                    ObjectNode scheduleNode = (ObjectNode) root.get("schedule");
                    ArrayNode days = (ArrayNode) scheduleNode.get("days");

                    for (int dayIdx = 0; dayIdx < days.size(); dayIdx++) {
                        JsonNode dayNode = days.get(dayIdx);
                        boolean isLastDay = (dayIdx == days.size() - 1);
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

                        List<ObjectNode> placeNodes = new ArrayList<>();
                        for (JsonNode p : places) placeNodes.add((ObjectNode) p);

                        List<ObjectNode> withCoords = placeNodes.stream()
                            .filter(p -> p.has("lat") && p.has("lng"))
                            .collect(Collectors.toList());
                        List<ObjectNode> withoutCoords = placeNodes.stream()
                            .filter(p -> !(p.has("lat") && p.has("lng")))
                            .collect(Collectors.toList());

                        // 최적 동선 정렬 및 시간 배정 적용
                        List<ObjectNode> ordered = optimalOrder(withCoords, isLastDay);
                        assignTimesForDay(ordered, mapper);
                        ordered.addAll(withoutCoords);

                        places.removeAll();
                        for (ObjectNode o : ordered) places.add(o);
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

    private JsonNode parseAiJson(String raw, ObjectMapper mapper) {
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

    private void assignTimesForDay(List<ObjectNode> orderedPlaces, ObjectMapper mapper) {
        if (orderedPlaces.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedPlaces.size(); i++) {
            ObjectNode p = orderedPlaces.get(i);
            sb.append(i + 1).append(". ").append(p.get("name").asText())
              .append(" (").append(p.get("category").asText()).append(")\n");
        }

        String prompt = String.format("""
            다음은 하루 동안 방문할 장소들을 실제 이동 동선 순서대로 나열한 것입니다.
            각 장소마다 현실적인 방문 시작~종료 시간(HH:mm~HH:mm)을 배정해주세요.

            [방문 순서]
            %s

            고려사항:
            - 등산(한라산 등)처럼 오래 걸리는 활동은 충분한 시간을 배정하세요(예: 07:00~17:00).
            - 식사는 아침(08:00~10:00), 점심(12:00~14:00), 저녁(18:00~20:00) 시간대를 고려하세요.
            - 관광지는 1~2시간, 카페는 1시간 내외로 배정하세요.
            - 시간이 겹치지 않고, 앞 장소 종료 후 다음 장소가 이어지도록 하세요.

            반드시 아래 JSON 형식으로만 응답하세요:
            { "times": ["07:00~17:00", "17:30~18:30", "..."] }
            """, sb.toString());

        try {
            String response = aiService.chatWithGemini(List.of(new ChatMessageDto("user", prompt)));
            JsonNode root = parseAiJson(response, mapper);
            if (root.has("times")) {
                ArrayNode times = (ArrayNode) root.get("times");
                for (int i = 0; i < orderedPlaces.size() && i < times.size(); i++) {
                    orderedPlaces.get(i).put("recommendedTime", times.get(i).asText());
                }
            }
        } catch (Exception e) {
            System.out.println("시간 재배정 실패: " + e.getMessage());
        }
    }

    private List<ObjectNode> optimalOrder(List<ObjectNode> places, boolean endNearAirport) {
        if (places.size() <= 1) return new ArrayList<>(places);
        if (places.size() > 8) return nearestNeighborOrder(places);

        List<List<ObjectNode>> permutations = new ArrayList<>();
        permute(new ArrayList<>(places), 0, permutations);

        List<ObjectNode> best = null;
        double bestDistance = Double.MAX_VALUE;

        for (List<ObjectNode> perm : permutations) {
            double total = 0;
            for (int i = 0; i < perm.size() - 1; i++) {
                total += haversine(
                    perm.get(i).get("lat").asDouble(), perm.get(i).get("lng").asDouble(),
                    perm.get(i + 1).get("lat").asDouble(), perm.get(i + 1).get("lng").asDouble()
                );
            }
            if (endNearAirport) {
                ObjectNode last = perm.get(perm.size() - 1);
                total += haversine(last.get("lat").asDouble(), last.get("lng").asDouble(), AIRPORT_LAT, AIRPORT_LNG);
            }
            if (total < bestDistance) {
                bestDistance = total;
                best = perm;
            }
        }
        return best;
    }

    private void permute(List<ObjectNode> list, int k, List<List<ObjectNode>> result) {
        if (k == list.size()) {
            result.add(new ArrayList<>(list));
            return;
        }
        for (int i = k; i < list.size(); i++) {
            Collections.swap(list, k, i);
            permute(list, k + 1, result);
            Collections.swap(list, k, i);
        }
    }

    private List<ObjectNode> nearestNeighborOrder(List<ObjectNode> places) {
        if (places.isEmpty()) return new ArrayList<>();
        List<ObjectNode> remaining = new ArrayList<>(places);
        List<ObjectNode> result = new ArrayList<>();
        ObjectNode current = remaining.remove(0);
        result.add(current);

        while (!remaining.isEmpty()) {
            ObjectNode nearest = null;
            double minDist = Double.MAX_VALUE;
            double curLat = current.get("lat").asDouble();
            double curLng = current.get("lng").asDouble();

            for (ObjectNode candidate : remaining) {
                double dist = haversine(curLat, curLng,
                    candidate.get("lat").asDouble(), candidate.get("lng").asDouble());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }
            result.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }
        return result;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String chatWithRetry(List<ChatMessageDto> messages) throws Exception {
        int attempts = 0;
        while (true) {
            try {
                return aiService.chatWithGemini(messages);
            } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable e) {
                attempts++;
                if (attempts >= 3) throw e;
                Thread.sleep(2000);
            }
        }
    }
}