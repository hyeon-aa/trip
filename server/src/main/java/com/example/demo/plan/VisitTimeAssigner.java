package com.example.demo.plan;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.ai.AiService;
import com.example.demo.plan.dto.ChatMessageDto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class VisitTimeAssigner {

    private final AiService aiService;
    private final AiResponseParser aiResponseParser;

    public VisitTimeAssigner(AiService aiService, AiResponseParser aiResponseParser) {
        this.aiService = aiService;
        this.aiResponseParser = aiResponseParser;
    }

    // fixedTimes는 orderedPlaces와 같은 순서/크기 — null이면 새로 시간 배정이
    // 필요한 장소, 값이 있으면 이전 턴부터 유지되는 장소라 그 시간을 그대로
    // 확정한다(#40). 유지 장소는 이미 정답을 아니까 Gemini에게 다시 물어보지
    // 않는다 — 새로 배정이 필요한 장소만 호출 대상에 포함시킨다.
    public void assignTimesForDay(
        List<ObjectNode> orderedPlaces,
        List<String> fixedTimes,
        String conversationContext,
        boolean isFirstDay,
        boolean isLastDay,
        Integer minStartMinutes
    ) {
        if (orderedPlaces.isEmpty()) return;

        // 유지되는 장소는 먼저 확정해둔다.
        for (int i = 0; i < orderedPlaces.size(); i++) {
            if (fixedTimes.get(i) != null) {
                orderedPlaces.get(i).put("recommendedTime", fixedTimes.get(i));
            }
        }

        List<ObjectNode> needsAssignment = new ArrayList<>();
        for (int i = 0; i < orderedPlaces.size(); i++) {
            if (fixedTimes.get(i) == null) needsAssignment.add(orderedPlaces.get(i));
        }
        if (needsAssignment.isEmpty()) return; // 전부 유지 대상이면 Gemini 호출 자체를 생략

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedPlaces.size(); i++) {
            ObjectNode p = orderedPlaces.get(i);
            sb.append(i + 1).append(". ").append(p.get("name").asText())
              .append(" (").append(p.get("category").asText()).append(")");
            String fixed = fixedTimes.get(i);
            if (fixed != null) {
                sb.append(" — 이미 ").append(fixed).append("로 정해져 있습니다(참고용, 응답에 포함할 필요 없음).");
            } else {
                sb.append(" — 새로 시간을 정해주세요.");
            }
            sb.append("\n");
        }

        StringBuilder dayConstraints = new StringBuilder();
        if (isFirstDay) {
            dayConstraints.append("- 이 날은 여행 첫째 날입니다. 대화에서 첫날 도착 시간이 언급됐다면, ")
                .append("그 시간 이전에는 어떤 장소도 배정하지 마세요.\n");
        }
        if (isLastDay) {
            dayConstraints.append("- 이 날은 여행 마지막 날입니다. 대화에서 출발(비행기) 시간이 언급됐다면, ")
                .append("그 시간에 늦지 않도록 마지막 장소 종료 시간과 공항 이동 여유를 확보하세요.\n");
        }
        if (minStartMinutes != null) {
            dayConstraints.append(String.format(
                "- 새로 시간을 정하는 장소는 반드시 %02d:%02d 이후에 배정하세요.\n",
                minStartMinutes / 60, minStartMinutes % 60
            ));
        }

        String prompt = String.format("""
            다음은 하루 동안 방문할 장소들을 실제 이동 동선 순서대로 나열한 것입니다.
            새로 시간을 정해달라고 표시된 장소만 현실적인 방문 시작~종료 시간
            (HH:mm~HH:mm)을 배정해주세요.

            [지금까지의 대화 - 도착/출발 시간 제약 참고용]
            %s

            [방문 순서]
            %s

            고려사항:
            - 등산(한라산 등)처럼 오래 걸리는 활동은 충분한 시간을 배정하세요(예: 07:00~17:00).
            - 식사는 아침(08:00~10:00), 점심(12:00~14:00), 저녁(18:00~20:00) 시간대를 고려하세요.
            - 관광지는 1~2시간, 카페는 1시간 내외로 배정하세요.
            - 시간이 겹치지 않고, 앞뒤 장소(이미 정해진 장소 포함)와 자연스럽게 이어지도록 하세요.
            %s
            반드시 아래 JSON 형식으로만 응답하세요(새로 시간을 정해달라고 한 장소만,
            이미 정해진 장소는 포함하지 마세요):
            { "times": [{ "name": "장소 이름", "time": "07:00~17:00" }, ...] }
            """, conversationContext, sb.toString(), dayConstraints.toString());

        try {
            String response = aiService.chatWithGemini(List.of(new ChatMessageDto("user", prompt)));
            JsonNode root = aiResponseParser.parse(response);

            // 이름+시간 짝으로 응답을 받는다 — 배열 순서가 요청 순서와 달라져도
            // (모델이 내부적으로 재배열해서 답해도) 엉뚱한 장소에 시간이
            // 배정되는 사고가 안 난다(#41, 실사용 재현 확인).
            List<String[]> responseEntries = new ArrayList<>();
            if (root.has("times")) {
                for (JsonNode entry : (ArrayNode) root.get("times")) {
                    if (entry.has("name") && entry.has("time")) {
                        responseEntries.add(new String[]{entry.get("name").asText(), entry.get("time").asText()});
                    }
                }
            }

            List<ObjectNode> unmatched = new ArrayList<>();
            for (ObjectNode place : needsAssignment) {
                String name = place.get("name").asText();
                String matchedTime = null;
                for (Iterator<String[]> it = responseEntries.iterator(); it.hasNext(); ) {
                    String[] entry = it.next();
                    if (entry[0].equals(name)) {
                        matchedTime = entry[1];
                        it.remove();
                        break;
                    }
                }
                if (matchedTime != null) {
                    place.put("recommendedTime", matchedTime);
                } else {
                    unmatched.add(place);
                }
            }
            // 이름 매칭에 실패한 나머지(오타 등, 드묾)는 남은 응답과 순서대로
            // 폴백 매칭한다 — 최소한 예전(인덱스만 믿던) 수준은 유지한다.
            for (int i = 0; i < unmatched.size() && i < responseEntries.size(); i++) {
                unmatched.get(i).put("recommendedTime", responseEntries.get(i)[1]);
            }
        } catch (Exception e) {
            System.out.println("시간 재배정 실패: " + e.getMessage());
            // 실패해도 유지 장소의 시간만큼은 이미 위에서 채워졌으니 그대로 남는다.
        }
    }
}
