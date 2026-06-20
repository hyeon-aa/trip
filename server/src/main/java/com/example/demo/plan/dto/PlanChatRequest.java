package com.example.demo.plan.dto;

import java.util.List;

public record PlanChatRequest(
    String message,
    List<ChatMessageDto> history
) {
}