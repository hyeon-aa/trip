package com.example.demo.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
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
    private final RouteOptimizer routeOptimizer;
    private final AiResponseParser aiResponseParser;
    private final PlaceGroupingService placeGroupingService;
    private final VisitTimeAssigner visitTimeAssigner;
    private final ApplicationEventPublisher eventPublisher;

    public PlanChatController(
        AiService aiService,
        WishlistRepository wishlistRepository,
        JejuPlaceRepository jejuPlaceRepository,
        RouteOptimizer routeOptimizer,
        AiResponseParser aiResponseParser,
        PlaceGroupingService placeGroupingService,
        VisitTimeAssigner visitTimeAssigner,
        ApplicationEventPublisher eventPublisher
    ) {
        this.aiService = aiService;
        this.wishlistRepository = wishlistRepository;
        this.jejuPlaceRepository = jejuPlaceRepository;
        this.routeOptimizer = routeOptimizer;
        this.aiResponseParser = aiResponseParser;
        this.placeGroupingService = placeGroupingService;
        this.visitTimeAssigner = visitTimeAssigner;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody PlanChatRequest request) {
        // 메인 일정 생성(Gemini 1회) + 날짜별 시간 배정(Gemini N회, 순차 호출)까지
        // 끝나야 응답이 나가므로, 60초는 다일차 일정에서 너무 빠듯하다 (이슈 #42
        // 실사용 검증 중 실제로 503을 확인함). 180초로 넉넉하게 잡는다.
        SseEmitter emitter = new SseEmitter(180_000L);
        ObjectMapper mapper = new ObjectMapper();

        String message = request.message();
        List<ChatMessageDto> history = request.history();
        Double accommodationLat = request.accommodationLat();
        Double accommodationLng = request.accommodationLng();

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

        String placesStr = placeGroupingService.buildPlacesPrompt(placeIdMap);

        // 6. 시스템 프롬프트 조립
        String systemPrompt = String.format("""
            당신은 수천 명의 여행 일정을 설계해 온 전문 여행 플래너입니다.

            [대화 진행 규칙]
            1. 다음 정보를 대화를 통해 순서대로 하나씩 파악하세요. 이 3가지를 물을 때는
               반드시 options에 선택 가능한 보기를 포함하세요 (사용자가 직접 입력할 수도
               있으니 자유 응답도 항상 허용됩니다).
               - 여행 스타일 (자연/맛집/액티비티/휴양 등) → options 예: ["자연", "맛집", "액티비티", "휴양"]
               - 동행자 (혼자/연인/가족/친구) → options 예: ["혼자", "연인", "가족", "친구"]
               - 여행 기간 (몇 박 며칠) → options 예: ["당일치기", "1박 2일", "2박 3일", "3박 4일"]
            2. 사용자가 이미 답한 정보는 절대 다시 묻지 마세요.
            3. 사용자가 말하지 않은 내용을 추측하거나 단정짓지 마세요.
            4. 사용자가 한 메시지에 여러 정보를 한꺼번에 알려주면 추가 질문 없이 바로 일정을 생성하세요.
            5. 부족한 정보만 한 번에 한 가지씩 자연스럽게 질문하세요.
            6. 위 3가지 정보가 모두 파악되면 전체 일정을 생성하세요.
            7. 이미 일정을 만든 후 유저가 수정을 요청하면 기존 맥락을 유지하면서 일정을 수정하세요.
            8. 첫째 날 출발 시간이나 마지막 날 비행기 시간을 사용자가 언급하면, 그 시간에 맞춰 일정을 조정하세요.
            9. 같은 날에는 반드시 같은 권역의 장소를 우선 사용하세요.
            10. 동부/서부/남부/제주시를 하루에 섞지 마세요.
            11. 같은 권역 안에서도 장소 목록에 표시된 읍/면/동 하위 그룹을 최대한
                하나로 통일해서 하루 동선이 여러 읍면동에 흩어지지 않게 하세요
                (예: 1일차는 서귀포시 위주, 2일차는 애월읍 위주).
            12. 이동시간 최소화를 최우선으로 고려하세요.
            13. 마지막 날은 공항 접근성을 고려하세요.
            14. 첫날 도착 시간이 있다면 그 시간 이전 일정은 배치하지 마세요.
            15. 마지막날 비행 시간이 있다면 공항 이동시간을 확보하세요.
            16. 같은 장소를 여러 날에 중복 배치하지 마세요. 이미 다른 날 일정에 넣은 장소는
                제외하고, 항상 새로운 장소로만 채우세요.

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
               - 등산(한라산 등), 장시간 액티비티가 포함된 날: 3곳
               - 가벼운 관광/휴양 위주인 날: 4~5곳
            2. 모든 날은 최소 3곳, 최대 5곳 이내로 구성하세요. 휴식 위주 여행이라도
               일정이 휑하게 느껴지지 않도록 카페, 산책로 등 가벼운 장소를 채워 최소
               3곳은 유지하세요.
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
                String response = aiService.chatWithGemini(messages);
                JsonNode root = aiResponseParser.parse(response);
                String type = root.has("type") ? root.get("type").asText() : "";

                if (type.equals("schedule") && root.has("schedule")) {
                    ObjectNode scheduleNode = (ObjectNode) root.get("schedule");
                    ArrayNode days = (ArrayNode) scheduleNode.get("days");

                    String conversationContext = buildConversationText(history, message);

                    // 응답이 끝까지 성공했을 때만 이벤트를 발행하기 위해 모아뒀다가
                    // 마지막에 한꺼번에 발행한다 (뒤 날짜 처리 중 예외가 나서 전체
                    // 요청이 실패하면, 앞서 처리된 날짜의 장소도 "일정에 포함됨"으로
                    // 잘못 발행되면 안 되므로).
                    List<PlaceIncludedInScheduleEvent> scheduleEvents = new ArrayList<>();

                    for (int dayIdx = 0; dayIdx < days.size(); dayIdx++) {
                        JsonNode dayNode = days.get(dayIdx);
                        boolean isFirstDay = (dayIdx == 0);
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
                                scheduleEvents.add(
                                    new PlaceIncludedInScheduleEvent("jeju_place", p.getId(), p.getName())
                                );
                            } else if (wishlistIdMap.containsKey(id)) {
                                Wishlist w = wishlistIdMap.get(id);
                                placeObj.put("name", w.getName());
                                placeObj.put("category", w.getCategory());
                                placeObj.put("lat", w.getLat());
                                placeObj.put("lng", w.getLng());
                                scheduleEvents.add(
                                    new PlaceIncludedInScheduleEvent("wishlist", w.getId(), w.getName())
                                );
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
                        // 매일 숙소에서 출발해서(있으면) 숙소로 돌아오는 걸 기본으로 하되,
                        // 마지막 날만 공항에서 끝나는 걸로 앵커를 바꾼다.
                        Double endLat;
                        Double endLng;
                        if (isLastDay) {
                            endLat = AIRPORT_LAT;
                            endLng = AIRPORT_LNG;
                        } else {
                            endLat = accommodationLat;
                            endLng = accommodationLng;
                        }
                        List<ObjectNode> ordered = routeOptimizer.optimalOrder(
                            withCoords, accommodationLat, accommodationLng, endLat, endLng
                        );
                        // 도착/출발 시간 제약은 첫/마지막 날에만 의미가 있으므로, 그 외
                        // 날짜는 대화 전체를 프롬프트에 실어 보내지 않는다(토큰 낭비 방지).
                        String dayConversationContext = (isFirstDay || isLastDay) ? conversationContext : "";
                        visitTimeAssigner.assignTimesForDay(ordered, dayConversationContext, isFirstDay, isLastDay);
                        ordered.addAll(withoutCoords);

                        places.removeAll();
                        for (ObjectNode o : ordered) places.add(o);
                    }

                    // 모든 날짜가 예외 없이 다 처리된 뒤에만(=응답이 성공적으로
                    // 만들어진 뒤에만) 이벤트를 발행한다.
                    scheduleEvents.forEach(eventPublisher::publishEvent);
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

    private String buildConversationText(List<ChatMessageDto> history, String message) {
        StringBuilder sb = new StringBuilder();
        if (history != null) {
            for (ChatMessageDto m : history) {
                sb.append(m.role()).append(": ").append(m.content()).append("\n");
            }
        }
        sb.append("user: ").append(message).append("\n");
        return sb.toString();
    }
}
