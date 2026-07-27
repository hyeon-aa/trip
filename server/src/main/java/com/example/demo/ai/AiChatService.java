package com.example.demo.ai;

import java.util.List;

import com.example.demo.plan.dto.ChatMessageDto;

// PlanChatController/VisitTimeAssigner가 Gemini와 실제로 주고받는 두 메서드만
// 인터페이스로 뽑아둔다 — e2e 테스트 프로필에서 Gemini 호출부만 스텁으로
// 갈아끼우기 위한 최소한의 확장 지점(AiChatServiceTestConfig 참고).
public interface AiChatService {

    String createEmbedding(String text);

    String chatWithGemini(List<ChatMessageDto> messages);
}
