package com.example.demo.ai;

import com.example.demo.plan.dto.ChatMessageDto;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

// Playwright E2E(이슈 #48)에서 실제 Gemini 쿼터를 쓰지 않으면서도 서버의
// 나머지 경로(벡터 검색, 동선 최적화, 이벤트 발행 등)는 전부 진짜로 태우기
// 위한 스텁. "e2e" 프로필이 활성화됐을 때만 실제 AiService 대신 주입된다
// (`bootRun`은 main 소스셋만 보고 test 소스셋은 안 봐서, 진짜 서버를 띄워야
// 하는 이 용도로는 test가 아니라 main에 둬야 한다 — @Profile("e2e")로 평소
// 실행(dev/기본 프로필)에는 절대 안 뜨게 막는다).
//
// PlanChatController와 VisitTimeAssigner 둘 다 AiChatService.chatWithGemini를
// 부르는데, 호출 형태로 어느 쪽인지 구분한다 — PlanChatController는 항상
// system 메시지(대규모 프롬프트)를 포함해서 보내고, VisitTimeAssigner는 매번
// user 메시지 하나만 보낸다(VisitTimeAssigner.java 참고).
@Service
@Profile("e2e")
@Primary
public class StubAiChatService implements AiChatService {

  // pgvector 컬럼이 vector(3072)라 차원 수를 맞춰야 CAST(... AS vector)가
  // 성공한다 — 값 자체는 의미 없다(테스트 DB가 소량이라 전부 top-N 안에
  // 들어오므로 유사도 순서는 신경 쓰지 않는다).
  private static final int EMBEDDING_DIMENSIONS = 3072;
  private static final Pattern PLACE_ID_PATTERN = Pattern.compile("\\[(p\\d+)\\]");

  @Override
  public String createEmbedding(String text) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
      if (i > 0) sb.append(',');
      sb.append('0');
    }
    return sb.append(']').toString();
  }

  @Override
  public String chatWithGemini(List<ChatMessageDto> messages) {
    boolean isMainScheduleCall = messages.stream().anyMatch(m -> "system".equals(m.role()));
    return isMainScheduleCall ? buildScheduleResponse(messages) : buildTimesResponse(messages);
  }

  // 실제 시스템 프롬프트에 박혀 들어간 "[추천 가능한 실제 제주도 장소]" 목록
  // ([p1] 이름 (카테고리)...)에서 이번 요청에 실제로 유효한 id를 뽑아 그
  // id들로만 일정을 구성한다 — 하드코딩된 id를 쓰면 그 턴의 실제 후보 목록에
  // 없을 수 있어(PlanChatController의 id-resolve 로직, 오늘 이슈 #40 작업
  // 중 확인한 것과 동일한 제약) name/lat/lng가 비어버린다.
  private String buildScheduleResponse(List<ChatMessageDto> messages) {
    String systemContent =
        messages.stream()
            .filter(m -> "system".equals(m.role()))
            .findFirst()
            .map(ChatMessageDto::content)
            .orElse("");

    Set<String> ids = new LinkedHashSet<>();
    Matcher matcher = PLACE_ID_PATTERN.matcher(systemContent);
    while (matcher.find()) {
      ids.add(matcher.group(1));
    }
    List<String> idList = ids.stream().limit(6).collect(Collectors.toList());

    List<String> day1 = idList.subList(0, Math.min(3, idList.size()));
    List<String> day2 = idList.size() > 3 ? idList.subList(3, idList.size()) : List.of();

    String day1Places =
        day1.stream()
            .map(
                id ->
                    "{\"id\":\""
                        + id
                        + "\",\"reason\":\"E2E 테스트용 추천 이유\",\"recommendedTime\":\"10:00~11:00\"}")
            .collect(Collectors.joining(","));
    String day2Places =
        day2.stream()
            .map(
                id ->
                    "{\"id\":\""
                        + id
                        + "\",\"reason\":\"E2E 테스트용 추천 이유\",\"recommendedTime\":\"10:00~11:00\"}")
            .collect(Collectors.joining(","));

    String daysJson =
        day2Places.isEmpty()
            ? "[{\"day\":1,\"places\":[" + day1Places + "]}]"
            : "[{\"day\":1,\"places\":["
                + day1Places
                + "]},{\"day\":2,\"places\":["
                + day2Places
                + "]}]";

    return "{\"type\":\"schedule\",\"message\":\"E2E 테스트용 일정이에요!\",\"schedule\":{\"days\":"
        + daysJson
        + "}}";
  }

  // VisitTimeAssigner는 "1. 이름 (카테고리) — 새로 시간을 정해주세요." 형태로
  // 새로 배정이 필요한 장소만 표시해서 보낸다(#41 이후 이름+시간 쌍으로 응답해야
  // 함 — 인덱스로만 응답하면 실제 코드의 이름 매칭 로직과 안 맞아 시간이 아예
  // 안 배정된다). 이 줄에서 이름만 뽑아 더미 시간과 짝지어 돌려준다.
  private static final Pattern NEEDS_TIME_PATTERN =
      Pattern.compile("^\\d+\\. (.+?) \\([^)]*\\) — 새로 시간을 정해주세요\\.$", Pattern.MULTILINE);
  private static final String[] DUMMY_TIME_SLOTS = {
    "10:00~11:00", "11:30~12:30", "13:00~14:00", "14:30~15:30", "16:00~17:00", "17:30~18:30"
  };

  private String buildTimesResponse(List<ChatMessageDto> messages) {
    String userContent =
        messages.stream()
            .filter(m -> "user".equals(m.role()))
            .findFirst()
            .map(ChatMessageDto::content)
            .orElse("");

    List<String> names = new java.util.ArrayList<>();
    Matcher matcher = NEEDS_TIME_PATTERN.matcher(userContent);
    while (matcher.find()) {
      names.add(matcher.group(1));
    }

    List<String> entryList = new java.util.ArrayList<>();
    for (int i = 0; i < names.size(); i++) {
      String name = names.get(i).replace("\"", "\\\"");
      String time = DUMMY_TIME_SLOTS[i % DUMMY_TIME_SLOTS.length];
      entryList.add("{\"name\":\"" + name + "\",\"time\":\"" + time + "\"}");
    }
    return "{\"times\":[" + String.join(",", entryList) + "]}";
  }
}
