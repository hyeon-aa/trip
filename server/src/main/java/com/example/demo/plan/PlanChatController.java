package com.example.demo.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.ai.AiChatService;
import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.plan.dto.ChatMessageDto;
import com.example.demo.plan.dto.PlanChatRequest;
import com.example.demo.plan.dto.ScheduleDto;
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

    private final AiChatService aiService;
    private final WishlistRepository wishlistRepository;
    private final JejuPlaceRepository jejuPlaceRepository;
    private final RouteOptimizer routeOptimizer;
    private final AiResponseParser aiResponseParser;
    private final PlaceGroupingService placeGroupingService;
    private final VisitTimeAssigner visitTimeAssigner;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentScheduleMerger currentScheduleMerger;
    private final ExecutorService visitTimeAssignerExecutor;
    private final PlanChatResponseSchemaBuilder responseSchemaBuilder;
    private final TravelTimeService travelTimeService;

    public PlanChatController(
        AiChatService aiService,
        WishlistRepository wishlistRepository,
        JejuPlaceRepository jejuPlaceRepository,
        RouteOptimizer routeOptimizer,
        AiResponseParser aiResponseParser,
        PlaceGroupingService placeGroupingService,
        VisitTimeAssigner visitTimeAssigner,
        ApplicationEventPublisher eventPublisher,
        CurrentScheduleMerger currentScheduleMerger,
        ExecutorService visitTimeAssignerExecutor,
        PlanChatResponseSchemaBuilder responseSchemaBuilder,
        TravelTimeService travelTimeService
    ) {
        this.aiService = aiService;
        this.wishlistRepository = wishlistRepository;
        this.jejuPlaceRepository = jejuPlaceRepository;
        this.routeOptimizer = routeOptimizer;
        this.aiResponseParser = aiResponseParser;
        this.placeGroupingService = placeGroupingService;
        this.visitTimeAssigner = visitTimeAssigner;
        this.eventPublisher = eventPublisher;
        this.visitTimeAssignerExecutor = visitTimeAssignerExecutor;
        this.currentScheduleMerger = currentScheduleMerger;
        this.responseSchemaBuilder = responseSchemaBuilder;
        this.travelTimeService = travelTimeService;
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
        // id는 매 요청 1부터 다시 매기는 임시 번호 대신, DB의 실제 PK를 그대로 쓴다
        // ("w" + Wishlist.id) — 그래야 같은 장소가 항상 같은 id를 가져서, 턴이
        //바뀌어도 CurrentScheduleMerger가 이름/좌표로 퍼지 매칭할 필요 없이
        // findById 하나로 바로 찾을 수 있다 (#40 리팩터링).
        List<Wishlist> wishlist = wishlistRepository.findAll();
        Map<String, Wishlist> wishlistIdMap = wishlist.stream()
            .collect(Collectors.toMap(
                w -> "w" + w.getId(), w -> w, (a, b) -> a, java.util.LinkedHashMap::new));

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
        String targetRegion = detectRegion(conversationText);
        List<String> targetCategories = detectCategories(conversationText);

        // 일정 수정 턴([현재 일정]이 있는 요청)에서는 이번 메시지에 명시된 지역/
        // 카테고리를 우선한다. conversationText는 history 전체가 누적돼 있어서,
        // 예를 들어 온보딩 때 말한 "오름/자연경관"이 계속 남아있는 채로 "카페로
        // 바꿔줘"라고 새로 요청하면 감지된 카테고리가 2개(자연+음식)가 되는데,
        // 누적된 대화 쪽 카테고리(자연)가 이번에 요청한 카페를 밀어낼 수 있으므로
        // (#40 실사용 검증 중 확인 — "산 빼고 카페로 바꿔줘"가 반영 안 되고 다른
        // 오름으로 대체됨), 최신 메시지에서 뭐라도 잡히면 그걸로 완전히 덮어쓴다.
        if (request.currentSchedule() != null) {
            List<String> latestCategories = detectCategories(message);
            if (!latestCategories.isEmpty()) targetCategories = latestCategories;
            String latestRegion = detectRegion(message);
            if (latestRegion != null) targetRegion = latestRegion;
        }

        // 4. 질문 기반 임베딩 생성 및 하이브리드 필터링 검색 수행
        String queryEmbedding = aiService.createEmbedding(conversationText);

        // 새로 개편한 레포지토리의 findSimilarPlacesWithFilter 메서드 호출.
        // 카테고리가 여러 개 감지돼도(예: "바다/해변" + "맛집 탐방" 동시 선택)
        // 필터를 아예 끄지 않고, 감지된 카테고리들의 합집합으로 검색한다 —
        // 사용자가 명시적으로 고른 조합인데 필터를 안 걸면 오히려 관련 없는
        // 카테고리(문화시설/레포츠 등)까지 섞여 들어온다.
        List<JejuPlace> relatedPlaces = jejuPlaceRepository.findSimilarPlacesWithFilter(
            queryEmbedding,
            targetRegion,                        // 파싱된 지역 조건 (없으면 null 전달되어 무시됨)
            toPostgresArrayLiteral(targetCategories), // 파싱된 카테고리 목록 (비어있으면 무시됨)
            50                                    // AI에게 줄 추천 후보 풀 개수 (넉넉하게 50개)
        );

        // 만약 조건 필터링 조건이 너무 엄격해서 결과가 없으면, 전체에서 검색하도록 안전장치 작동
        if (relatedPlaces.isEmpty()) {
            relatedPlaces = jejuPlaceRepository.findSimilarPlacesWithFilter(queryEmbedding, null, "{}", 50);
        }

        // 로그 출력 (디버깅용)
        relatedPlaces.forEach(p ->
            System.out.println(
                "[" + p.getRegion() + "] " + p.getName() + " | " + p.getCategory()
            )
        );

        // 5. 추천 장소 리스트 ID 매핑 및 권역별 프롬프트 빌드 (마찬가지로 DB 실제 PK 사용)
        Map<String, JejuPlace> placeIdMap = relatedPlaces.stream()
            .collect(Collectors.toMap(
                p -> "p" + p.getId(), p -> p, (a, b) -> a, java.util.LinkedHashMap::new));

        String currentScheduleSection = currentScheduleMerger.mergeAndBuildSection(
            request.currentSchedule(), placeIdMap, wishlistIdMap
        );
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
            7. 이미 일정을 만든 후 유저가 수정을 요청하면, 아래 [현재 일정] 섹션을 기준으로
               삼으세요.
               - 가장 먼저, 사용자가 이번 메시지에서 명시적으로 요청한 변경(예: "카페
                 추가해줘", "이 장소 빼줘", "다른 곳으로 바꿔줘")을 반드시 실제로
                 반영하세요. 요청받은 변경을 적용하지 않고 일정을 그대로 돌려주면
                 안 됩니다.
               - 언급된 날짜 안에서, 요청한 변경 외의 기존 장소는 [현재 일정]에 표시된
                 id 그대로 남겨두세요.
                 - "추가해줘"라면 기존 장소를 지우지 말고 새 장소만 더하세요.
                 - "빼줘"/"삭제해줘"처럼 순수 삭제를 요청한 경우에만 해당 장소를
                   지우고, 그 자리를 다른 장소로 채우지 마세요(장소 개수가 줄어듭니다).
                 - "바꿔줘"/"교체해줘"처럼 교체를 요청한 경우에는, 해당 장소를 지우는
                   데서 끝내지 말고 반드시 다른 새 장소로 채워 넣어서 장소 개수가
                   그대로 유지되게 하세요.
               - "오후부터는 바꿔줘", "3시 이후 일정만 바꿔줘", "저녁 일정만 바꿔줘"처럼
                 시간대를 기준으로 부분 교체를 요청하면, [현재 일정]에 각 장소 옆에
                 표시된 시간을 참고해서 해당 시간대에 걸치는 장소만 교체하고, 그 외
                 시간대의 장소는 id를 그대로 유지하세요("오전"은 대략 12시 이전, "오후"는
                 12시 이후, "저녁"은 18시 이후로 판단 — 사용자가 "5시 이후"처럼 구체적인
                 시간을 말하지 않는 한 이 기본값을 쓰세요). 시간이 표시되지 않은 장소는 이
                 판단에서 제외하고 id를 그대로 유지하세요.
               - "OO랑 XX 사이에 넣어줘", "OO 근처에 추가해줘"처럼 위치를 지정해서 새
                 장소를 추가/교체해달라고 하면, 장소 목록에 표시된 읍/면/동 그룹을 보고
                 언급된 기준 장소(들)와 같은 읍/면/동에 속한 후보를 우선 선택하세요 —
                 id는 정확한 좌표를 담고 있지 않아 실제 위치를 알 수 없으니, 읍/면/동이
                 지리적 근접성을 판단할 수 있는 유일한 단서입니다. 기준 장소 두 곳이
                 서로 다른 읍/면/동이면 그중 하나와 같은 읍/면/동을 우선하고, 정확히
                 "사이"에 있다고 단정하는 이유(reason)는 쓰지 마세요.
               - 언급되지 않은 날짜는 [현재 일정]에 표시된 id를 순서와 구성 그대로 다시
                 사용해서 완전히 동일하게 유지하세요. 절대로 언급되지 않은 날짜까지
                 새로 만들거나 장소를 바꾸지 마세요.
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
            """, placesStr, wishlistStr, currentScheduleSection);

        List<ChatMessageDto> messages = new ArrayList<>();
        messages.add(new ChatMessageDto("system", systemPrompt));
        if (history != null) messages.addAll(history);
        messages.add(new ChatMessageDto("user", message));

        // 프롬프트 텍스트("id는 목록 중에서만 선택하세요")는 부탁일 뿐이라, 이번
        // 턴에 실제로 유효한 id(placeIdMap + wishlistIdMap) 전체를 enum으로 건
        // JSON Schema를 같이 실어서 API 레벨에서 강제한다(이슈 #50). validIds가
        // 비어있으면(예: jeju_place가 아직 비어있는 신선한 환경) enum이 빈
        // "절대 만족 불가" 스키마가 되어버리므로, 이 경우엔 스키마를 아예 안
        // 실어서 기존처럼 프롬프트 텍스트 제약만 쓰는 쪽으로 안전하게 물러난다.
        Set<String> validIds = new LinkedHashSet<>();
        validIds.addAll(placeIdMap.keySet());
        validIds.addAll(wishlistIdMap.keySet());
        String responseSchema = validIds.isEmpty() ? null : responseSchemaBuilder.build(validIds);

        // 7. 비동기 쓰레드로 AI 요청 및 데이터 후처리 가공 (SSE 스트리밍 전송)
        new Thread(() -> {
            try {
                String response = aiService.chatWithGemini(messages, responseSchema);
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

                    // 날짜별 시간 배정(Gemini 호출)은 서로 완전히 독립적인데, 예전엔 이
                    // for문 안에서 날짜마다 순서대로 기다렸다 — 한 번에 10~25초씩 걸리는
                    // 호출이 N일치 쌓이면 다일차 일정일수록 응답이 그만큼 느려졌다(#42에서
                    // 이미 지연 원인으로 지목됨). id 치환/동선 최적화(순수 CPU)는 그대로
                    // 순차 처리하되, 시간 배정 호출만 따로 모아서 한꺼번에 병렬로 쏘고
                    // 전부 끝난 뒤에 결과를 JSON 트리에 반영한다. `ordered` 리스트를
                    // `assignTimesForDay` 호출이 아직 참조 중인 동안 다른 스레드에서
                    // 건드리면 안 되므로, withoutCoords를 다시 합치는 것도 전부 끝난
                    // 뒤로 미룬다.
                    record DayWork(ArrayNode places, List<ObjectNode> ordered, List<ObjectNode> withoutCoords) {
                    }
                    List<DayWork> dayWorks = new ArrayList<>();
                    List<CompletableFuture<Void>> timeAssignments = new ArrayList<>();

                    Integer minStartMinutes = detectTimeThresholdMinutes(message);

                    for (int dayIdx = 0; dayIdx < days.size(); dayIdx++) {
                        JsonNode dayNode = days.get(dayIdx);
                        boolean isFirstDay = (dayIdx == 0);
                        boolean isLastDay = (dayIdx == days.size() - 1);
                        int dayNumber = dayNode.has("day") ? dayNode.get("day").asInt() : dayIdx + 1;
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

                        // 이전 턴과 id가 같은(=유지되는) 장소는 시간도 그대로 보존한다 —
                        // 장소 하나만 바뀌어도 그 날 전체를 처음부터 다시 시간 배정하면
                        // 손 안 댄 장소까지 시간이 흔들렸다(#40 실사용 검증 중 확인 —
                        // "저녁만 바꿔줘" 요청에서 오후 장소 시간까지 밀려서 결과적으로
                        // "저녁"이라 부를 시간대 자체가 사라짐). null이면 새로 배정 필요.
                        Map<String, String> oldTimes = oldTimesForDay(request.currentSchedule(), dayNumber);
                        List<String> fixedTimes = new ArrayList<>();
                        for (ObjectNode o : ordered) {
                            String oid = o.has("id") ? o.get("id").asText() : null;
                            fixedTimes.add(oid != null ? oldTimes.get(oid) : null);
                        }

                        dayWorks.add(new DayWork(places, ordered, withoutCoords));
                        // 이동시간 조회(카카오모빌리티, 이슈 #44)도 시간 배정과 마찬가지로
                        // 날짜별로 병렬 실행되는 이 블록 안에서 수행한다 — 날짜 간
                        // 병렬성은 그대로 유지하면서, 같은 날 안의 여러 구간 조회도
                        // TravelTimeService 안에서 추가로 병렬화된다(중첩 병렬).
                        timeAssignments.add(CompletableFuture.runAsync(() -> {
                            List<Integer> travelMinutes =
                                travelTimeService.lookupConsecutiveTravelMinutes(ordered, visitTimeAssignerExecutor);
                            // 프론트에서도 장소 카드에 "이전 장소에서 이동 약 N분"을 보여줄 수
                            // 있게, i+1번째 장소(도착지)에 그 구간의 이동시간을 실어 보낸다.
                            // 이 값은 routeOptimizer가 정한 순서 기준이라, 아래에서
                            // recommendedTime 기준으로 최종 재정렬되면 이웃이 바뀌어 값이
                            // 살짝 안 맞을 수 있다(주로 부분 수정으로 일부 장소 시간이
                            // 고정된 턴에서만 발생하는 드문 경우) — 그래도 아예 표시 안
                            // 하는 것보다는 낫다고 판단해 그대로 둔다.
                            for (int i = 0; i < travelMinutes.size(); i++) {
                                Integer minutes = travelMinutes.get(i);
                                if (minutes != null) {
                                    ordered.get(i + 1).put("travelMinutesFromPrevious", minutes);
                                }
                            }
                            visitTimeAssigner.assignTimesForDay(
                                ordered, fixedTimes, dayConversationContext, isFirstDay, isLastDay,
                                minStartMinutes, travelMinutes
                            );
                        }, visitTimeAssignerExecutor));
                    }

                    // 날짜별 시간 배정 호출이 전부 끝날 때까지 대기 — 서로 독립적이라
                    // 순서 상관없이 동시에 진행되고, 가장 느린 호출 하나의 시간만큼만
                    // 기다리면 된다(예전엔 날짜 수만큼의 합산 시간을 기다렸음).
                    CompletableFuture.allOf(timeAssignments.toArray(new CompletableFuture[0])).join();

                    // 병렬 호출 결과를 순서대로 다시 JSON 트리에 반영.
                    // RouteOptimizer는 좌표 거리만 보고 순서를 정하는데, 유지되는
                    // 장소는 시간이 고정돼 있어서 동선 순서와 시간 순서가 어긋날 수
                    // 있다(예: 저녁에 새로 추가된 장소가 배열상 오후 장소보다 앞에
                    // 옴) — 화면에 보여지는 순서는 항상 "이른 시간 먼저"가 자연스러우
                    // 므로, 시간 배정이 다 끝난 뒤 recommendedTime 기준으로 다시
                    // 정렬한다.
                    for (DayWork work : dayWorks) {
                        work.ordered().sort(Comparator.comparingInt(PlanChatController::startMinutes));
                        work.ordered().addAll(work.withoutCoords());
                        work.places().removeAll();
                        for (ObjectNode o : work.ordered()) work.places().add(o);
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

    // currentSchedule의 특정 날짜에서, 장소 id → 그때의 recommendedTime 맵을 만든다.
    // 이번 턴 응답에 같은 id가 다시 나오면(=유지되는 장소) 이 시간을 그대로 쓴다.
    private Map<String, String> oldTimesForDay(ScheduleDto currentSchedule, int dayNumber) {
        if (currentSchedule == null || currentSchedule.days() == null) {
            return Map.of();
        }
        for (ScheduleDto.DayDto day : currentSchedule.days()) {
            if (day.day() != dayNumber || day.places() == null) {
                continue;
            }
            Map<String, String> result = new LinkedHashMap<>();
            for (ScheduleDto.PlaceDto p : day.places()) {
                if (p.id() != null && p.recommendedTime() != null) {
                    result.put(p.id(), p.recommendedTime());
                }
            }
            return result;
        }
        return Map.of();
    }

    // "오후부터는 바꿔줘" 같은 시간대 기준 부분 교체 요청에서, 새로 배정되는 장소가
    // 지켜야 할 최소 시작 시각(분 단위)을 찾는다. 사용자가 "5시 이후"처럼 구체적인
    // 시간을 말하면 그대로 쓰고(12 이하 숫자는 오후로 보정), 아니면 "저녁"=18시,
    // "오후"=12시를 기본값으로 쓴다("오전"은 굳이 하한을 걸 필요가 없어 생략).
    private static final Pattern TIME_MENTION_PATTERN = Pattern.compile("(\\d{1,2})시\\s*이후");

    private Integer detectTimeThresholdMinutes(String text) {
        Matcher m = TIME_MENTION_PATTERN.matcher(text);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            if (hour >= 1 && hour <= 11) hour += 12;
            return hour * 60;
        }
        if (text.contains("저녁")) return 18 * 60;
        if (text.contains("오후")) return 12 * 60;
        return null;
    }

    // "HH:mm~HH:mm" 형태의 recommendedTime에서 시작 시각을 분 단위로 뽑아낸다.
    // 정렬 전용이라, 형식이 이상하거나 없으면 맨 뒤로 보내고 조용히 넘어간다.
    private static int startMinutes(ObjectNode place) {
        if (!place.has("recommendedTime")) return Integer.MAX_VALUE;
        String start = place.get("recommendedTime").asText().split("~")[0].trim();
        try {
            String[] parts = start.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
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

    // List<String> → Postgres 배열 리터럴 문자열("{음식점,관광지}", 빈 리스트면 "{}").
    // 카테고리 값(음식점/관광지/문화시설/레포츠)은 콤마·중괄호·따옴표가 안 섞인
    // 고정된 값들이라 별도 이스케이프 없이 그대로 이어붙여도 안전하다.
    private String toPostgresArrayLiteral(List<String> values) {
        return "{" + String.join(",", values) + "}";
    }

    private String detectRegion(String text) {
        if (text.contains("서부")) return "서부";
        if (text.contains("동부")) return "동부";
        if (text.contains("남부")) return "남부";
        if (text.contains("제주시")) return "제주시";
        return null;
    }

    // 온보딩 스타일 버튼(PlanChat.tsx ONBOARDING_QUESTIONS[0].options)은 멀티셀렉트라
    // 여러 카테고리가 동시에 감지될 수 있다(예: "바다/해변" + "맛집 탐방") — 예전엔
    // 이럴 때 필터 자체를 꺼서 관련 없는 카테고리까지 다 섞였는데, 이제 감지된
    // 카테고리들의 합집합으로 필터링한다("역사/문화"는 "문화시설"이 아니라
    // "역사"/"문화"만 있어서 예전 키워드로는 전혀 감지가 안 됐고(#40 실사용 검증
    // 중 발견), "휴양/힐링"도 이 DB에서 휴양림 등이 실제로 관광지 카테고리로
    // 들어있어(예: 제주절물자연휴양림) 관광지 쪽으로 잡는 게 맞다).
    private List<String> detectCategories(String text) {
        List<String> categories = new ArrayList<>();
        boolean wantsFood = text.contains("맛집") || text.contains("흑돼지") || text.contains("식당") || text.contains("갈치") || text.contains("카페") || text.contains("커피") || text.contains("베이커리");
        boolean wantsNature = text.contains("관광지") || text.contains("자연") || text.contains("바다") || text.contains("숲") || text.contains("휴양") || text.contains("힐링");
        boolean wantsCulture = text.contains("문화시설") || text.contains("역사") || text.contains("문화") || text.contains("박물관") || text.contains("미술관") || text.contains("전시");
        boolean wantsSports = text.contains("레포츠") || text.contains("액티비티") || text.contains("서핑") || text.contains("스쿠버") || text.contains("승마");

        if (wantsFood) categories.add("음식점");
        if (wantsNature) categories.add("관광지");
        if (wantsCulture) categories.add("문화시설");
        if (wantsSports) categories.add("레포츠");
        return categories;
    }
}
