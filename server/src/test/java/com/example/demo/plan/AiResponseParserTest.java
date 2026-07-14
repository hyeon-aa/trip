package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class AiResponseParserTest {

    private final AiResponseParser parser = new AiResponseParser();

    @Test
    void 순수_JSON_문자열을_그대로_파싱한다() {
        JsonNode result = parser.parse("{\"type\":\"question\",\"message\":\"안녕하세요\"}");

        assertThat(result.get("type").asText()).isEqualTo("question");
        assertThat(result.get("message").asText()).isEqualTo("안녕하세요");
    }

    @Test
    void 마크다운_코드펜스로_감싼_JSON은_펜스를_제거하고_파싱한다() {
        String raw = "```json\n{\"type\":\"schedule\",\"message\":\"일정입니다\"}\n```";

        JsonNode result = parser.parse(raw);

        assertThat(result.get("type").asText()).isEqualTo("schedule");
    }

    @Test
    void JSON_앞뒤에_다른_텍스트가_있어도_중괄호_구간만_뽑아_파싱한다() {
        String raw = "여기 결과입니다: {\"type\":\"question\",\"message\":\"ok\"} 감사합니다.";

        JsonNode result = parser.parse(raw);

        assertThat(result.get("type").asText()).isEqualTo("question");
        assertThat(result.get("message").asText()).isEqualTo("ok");
    }

    @Test
    void JSON이_아니면_type_question_폴백으로_원문을_message에_담는다() {
        String raw = "죄송해요, 이해하지 못했어요.";

        JsonNode result = parser.parse(raw);

        assertThat(result.get("type").asText()).isEqualTo("question");
        assertThat(result.get("message").asText()).isEqualTo(raw);
    }
}
