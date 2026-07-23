package com.example.demo.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import com.example.demo.plan.dto.ChatMessageDto;
import com.example.demo.redis.RedisService;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;

class AiServiceTest {

    private final RedisService redisService = mock(RedisService.class);
    private final ChatClient chatClient = mock(ChatClient.class);
    private final AiService aiService = new AiService(redisService, builderReturning(chatClient));

    // ChatClient.Builder는 스프링이 자동 구성해주는 Bean이라, 테스트에서는
    // build()가 우리가 만든 chatClient 모킹 객체를 돌려주도록 직접 구성한다.
    private static ChatClient.Builder builderReturning(ChatClient chatClient) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        return builder;
    }

    @Test
    void 동일한_텍스트는_항상_같은_캐시_키를_만든다() {
        String key1 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");
        String key2 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void 다른_텍스트는_다른_캐시_키를_만든다() {
        String key1 = aiService.cacheKey("혼자 2박 3일 자연 위주 여행");
        String key2 = aiService.cacheKey("가족 3박 4일 맛집 위주 여행");

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void 캐시에_값이_있으면_API_호출_없이_그대로_반환한다() {
        when(redisService.get(anyString())).thenReturn("[0.1, 0.2, 0.3]");

        String result = aiService.createEmbedding("아무 텍스트");

        assertThat(result).isEqualTo("[0.1, 0.2, 0.3]");
        verify(redisService, never()).save(anyString(), anyString());
    }

    @Test
    void Redis_조회가_실패해도_예외를_던지지_않고_null을_반환한다() {
        when(redisService.get(anyString())).thenThrow(new RuntimeException("연결 실패"));

        String result = aiService.getCached("아무 키");

        assertThat(result).isNull();
    }

    @Test
    void Redis_저장이_실패해도_예외를_던지지_않는다() {
        doThrow(new RuntimeException("연결 실패")).when(redisService).save(anyString(), anyString());

        assertThatCode(() -> aiService.saveCache("아무 키", "아무 값")).doesNotThrowAnyException();
    }

    @Test
    void chatWithGemini는_system_메시지를_먼저_넣고_나머지는_순서대로_역할을_변환해서_호출한다() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("응답 내용");

        List<ChatMessageDto> messages = List.of(
            new ChatMessageDto("system", "너는 여행 상담사야"),
            new ChatMessageDto("user", "안녕"),
            new ChatMessageDto("assistant", "네 안녕하세요"),
            new ChatMessageDto("user", "제주도 추천해줘")
        );

        String result = aiService.chatWithGemini(messages);

        assertThat(result).isEqualTo("응답 내용");

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).prompt(promptCaptor.capture());
        List<Message> instructions = promptCaptor.getValue().getInstructions();

        assertThat(instructions).hasSize(4);
        assertThat(instructions.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(instructions.get(1)).isInstanceOf(UserMessage.class);
        assertThat(instructions.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(instructions.get(3)).isInstanceOf(UserMessage.class);
    }

    // GoogleGenAiChatModel은 Gemini 쪽 에러를 전부 평범한 RuntimeException으로
    // 감싸서 던지기 때문에, Spring AI의 RetryTemplate이 재시도 대상으로 인식하지
    // 못한다 (spring.ai.retry.max-attempts를 설정해도 적용 안 됨). 그래서
    // chatWithGemini가 직접 cause 체인을 보고 재시도하는지를 검증한다.
    @Test
    void chatWithGemini는_일시적_오류_5xx면_재시도해서_성공한다() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        RuntimeException wrapped503 = new RuntimeException(
            "Failed to generate content", new ServerException(503, "UNAVAILABLE", "일시적 오류"));
        when(callResponseSpec.content())
            .thenThrow(wrapped503)
            .thenReturn("재시도 후 성공");

        String result = aiService.chatWithGemini(List.of(new ChatMessageDto("user", "안녕")));

        assertThat(result).isEqualTo("재시도 후 성공");
        verify(callResponseSpec, times(2)).content();
    }

    @Test
    void chatWithGemini는_클라이언트_오류_4xx면_재시도하지_않고_바로_예외를_던진다() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        RuntimeException wrapped400 = new RuntimeException(
            "Failed to generate content", new ClientException(400, "INVALID_ARGUMENT", "잘못된 요청"));
        when(callResponseSpec.content()).thenThrow(wrapped400);

        List<ChatMessageDto> messages = List.of(new ChatMessageDto("user", "안녕"));

        assertThatThrownBy(() -> aiService.chatWithGemini(messages)).isSameAs(wrapped400);
        verify(callResponseSpec, times(1)).content();
    }

    @Test
    void chatWithGemini는_계속_일시적_오류면_최대_횟수까지만_재시도하고_예외를_던진다() {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt(any(Prompt.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        RuntimeException wrapped503 = new RuntimeException(
            "Failed to generate content", new ServerException(503, "UNAVAILABLE", "계속되는 오류"));
        when(callResponseSpec.content()).thenThrow(wrapped503);

        List<ChatMessageDto> messages = List.of(new ChatMessageDto("user", "안녕"));

        assertThatThrownBy(() -> aiService.chatWithGemini(messages)).isSameAs(wrapped503);
        verify(callResponseSpec, times(3)).content();
    }
}
