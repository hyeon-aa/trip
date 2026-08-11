package com.example.demo.ai;

import com.example.demo.plan.dto.ChatMessageDto;
import java.util.List;

// PlanChatController/VisitTimeAssigner가 Gemini와 실제로 주고받는 두 메서드만
// 인터페이스로 뽑아둔다 — e2e 테스트 프로필에서 Gemini 호출부만 스텁으로
// 갈아끼우기 위한 최소한의 확장 지점(AiChatServiceTestConfig 참고).
public interface AiChatService {

  String createEmbedding(String text);

  String chatWithGemini(List<ChatMessageDto> messages);

  // responseSchema는 이번 요청에서 응답 JSON이 반드시 만족해야 하는 JSON
  // Schema 문자열이다(place id enum 제약 등, 이슈 #50) — null이면 스키마
  // 없이 기존과 동일하게 호출한다. 기본 구현은 스키마를 무시하고 1-인자
  // 버전으로 위임하므로, StubAiChatService처럼 스키마를 굳이 강제할 필요가
  // 없는 구현체는 오버라이드하지 않아도 된다(스텁은 애초에 유효한 id만
  // 만들어내므로 스키마 검증이 필요 없다).
  default String chatWithGemini(List<ChatMessageDto> messages, String responseSchema) {
    return chatWithGemini(messages);
  }
}
