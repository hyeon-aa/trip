package com.example.demo.plan;

import java.util.Set;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// PlanChatController가 시스템 프롬프트로 "id는 반드시 목록 중에서만 선택하라"고
// 부탁하는 것과 별개로(이슈 #40 검증 중 실제로 벗어난 적은 없었지만 강제 수단은
// 아니었다), Gemini의 responseSchema에 place id를 그 턴의 실제 후보 id
// 목록(enum)으로 제약해서 API 레벨에서 강제한다(이슈 #50). 응답이 매 요청 두
// 형태("question"/"schedule")를 오가므로 oneOf로 나누지 않고, 두 형태의
// 필드를 전부 하나의 스키마 안에 넣되 필수(required)는 공통 필드(type/message)만
// 두는 방식으로 표현한다.
@Service
public class PlanChatResponseSchemaBuilder {

    private final ObjectMapper mapper = new ObjectMapper();

    public String build(Set<String> validIds) {
        ObjectNode idSchema = mapper.createObjectNode();
        idSchema.put("type", "string");
        ArrayNode idEnum = idSchema.putArray("enum");
        validIds.forEach(idEnum::add);

        ObjectNode placeSchema = mapper.createObjectNode();
        placeSchema.put("type", "object");
        ObjectNode placeProperties = placeSchema.putObject("properties");
        placeProperties.set("id", idSchema);
        placeProperties.putObject("reason").put("type", "string");
        placeProperties.putObject("recommendedTime").put("type", "string");
        placeSchema.putArray("required").add("id");

        ObjectNode placesArraySchema = mapper.createObjectNode();
        placesArraySchema.put("type", "array");
        placesArraySchema.set("items", placeSchema);

        ObjectNode daySchema = mapper.createObjectNode();
        daySchema.put("type", "object");
        ObjectNode dayProperties = daySchema.putObject("properties");
        dayProperties.putObject("day").put("type", "integer");
        dayProperties.set("places", placesArraySchema);
        ArrayNode dayRequired = daySchema.putArray("required");
        dayRequired.add("day");
        dayRequired.add("places");

        ObjectNode daysArraySchema = mapper.createObjectNode();
        daysArraySchema.put("type", "array");
        daysArraySchema.set("items", daySchema);

        ObjectNode scheduleSchema = mapper.createObjectNode();
        scheduleSchema.put("type", "object");
        scheduleSchema.putObject("properties").set("days", daysArraySchema);
        scheduleSchema.putArray("required").add("days");

        ObjectNode optionsArraySchema = mapper.createObjectNode();
        optionsArraySchema.put("type", "array");
        optionsArraySchema.putObject("items").put("type", "string");

        ObjectNode typeSchema = mapper.createObjectNode();
        typeSchema.put("type", "string");
        ArrayNode typeEnum = typeSchema.putArray("enum");
        typeEnum.add("question");
        typeEnum.add("schedule");

        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode rootProperties = root.putObject("properties");
        rootProperties.set("type", typeSchema);
        rootProperties.putObject("message").put("type", "string");
        rootProperties.set("options", optionsArraySchema);
        rootProperties.set("schedule", scheduleSchema);
        ArrayNode rootRequired = root.putArray("required");
        rootRequired.add("type");
        rootRequired.add("message");

        return root.toString();
    }
}
