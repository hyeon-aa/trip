package com.example.demo.plan;

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

    public void assignTimesForDay(
        List<ObjectNode> orderedPlaces,
        String conversationContext,
        boolean isFirstDay,
        boolean isLastDay
    ) {
        if (orderedPlaces.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedPlaces.size(); i++) {
            ObjectNode p = orderedPlaces.get(i);
            sb.append(i + 1).append(". ").append(p.get("name").asText())
              .append(" (").append(p.get("category").asText()).append(")\n");
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

        String prompt = String.format("""
            다음은 하루 동안 방문할 장소들을 실제 이동 동선 순서대로 나열한 것입니다.
            각 장소마다 현실적인 방문 시작~종료 시간(HH:mm~HH:mm)을 배정해주세요.

            [지금까지의 대화 - 도착/출발 시간 제약 참고용]
            %s

            [방문 순서]
            %s

            고려사항:
            - 등산(한라산 등)처럼 오래 걸리는 활동은 충분한 시간을 배정하세요(예: 07:00~17:00).
            - 식사는 아침(08:00~10:00), 점심(12:00~14:00), 저녁(18:00~20:00) 시간대를 고려하세요.
            - 관광지는 1~2시간, 카페는 1시간 내외로 배정하세요.
            - 시간이 겹치지 않고, 앞 장소 종료 후 다음 장소가 이어지도록 하세요.
            %s
            반드시 아래 JSON 형식으로만 응답하세요:
            { "times": ["07:00~17:00", "17:30~18:30", "..."] }
            """, conversationContext, sb.toString(), dayConstraints.toString());

        try {
            String response = aiService.chatWithGemini(List.of(new ChatMessageDto("user", prompt)));
            JsonNode root = aiResponseParser.parse(response);
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
}
