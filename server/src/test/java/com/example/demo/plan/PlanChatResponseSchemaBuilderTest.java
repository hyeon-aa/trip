package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PlanChatResponseSchemaBuilderTest {

    private final PlanChatResponseSchemaBuilder builder = new PlanChatResponseSchemaBuilder();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 넘겨준_id들만_place_id_enum에_포함된다() {
        Set<String> validIds = new LinkedHashSet<>();
        validIds.add("p1");
        validIds.add("p2");
        validIds.add("w3");

        JsonNode schema = mapper.readTree(builder.build(validIds));

        JsonNode idEnum = schema.at("/properties/schedule/properties/days/items/properties/places/items/properties/id/enum");
        assertThat(idEnum.isArray()).isTrue();
        assertThat(idEnum).extracting(JsonNode::asString).containsExactlyInAnyOrder("p1", "p2", "w3");
    }

    @Test
    void 유효하지_않은_id는_enum에_포함되지_않는다() {
        Set<String> validIds = Set.of("p1");

        JsonNode schema = mapper.readTree(builder.build(validIds));

        JsonNode idEnum = schema.at("/properties/schedule/properties/days/items/properties/places/items/properties/id/enum");
        assertThat(idEnum).extracting(JsonNode::asString).doesNotContain("p999");
    }

    @Test
    void type은_question과_schedule_두_값만_허용한다() {
        JsonNode schema = mapper.readTree(builder.build(Set.of("p1")));

        JsonNode typeEnum = schema.at("/properties/type/enum");
        assertThat(typeEnum).extracting(JsonNode::asString).containsExactlyInAnyOrder("question", "schedule");
    }

    @Test
    void 최상위_필수는_type과_message뿐이라_question과_schedule_응답_둘_다_허용한다() {
        JsonNode schema = mapper.readTree(builder.build(Set.of("p1")));

        JsonNode required = schema.get("required");
        assertThat(required).extracting(JsonNode::asString).containsExactlyInAnyOrder("type", "message");
    }
}
