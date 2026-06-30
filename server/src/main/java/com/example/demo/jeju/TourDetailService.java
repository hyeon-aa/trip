package com.example.demo.jeju;

import java.util.StringJoiner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class TourDetailService {

    @Value("${tourapi.service.key}")
    private String serviceKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    private static final String BASE_URL = "https://apis.data.go.kr/B551011/KorService2";
    private static final String MOBILE_OS = "ETC";
    private static final String MOBILE_APP = "Trip";
    private static final String RESPONSE_TYPE = "json";

    /**
     * 장소 개요(Overview) 조회
     */
    public String getOverview(String contentId) {
        try {
            String url = UriComponentsBuilder.fromUriString(BASE_URL + "/detailCommon2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", RESPONSE_TYPE)
                .queryParam("contentId", contentId)
                .queryParam("overviewYN", "Y")
                .queryParam("defaultYN", "Y")
                .build(true) // serviceKey 인코딩 깨짐 방지
                .toUriString();

            String response = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode item = getResponseItem(response);

            if (item.isArray() && !item.isEmpty()) {
                return item.get(0).path("overview").asText("").trim();
            }

        } catch (Exception e) {
            System.out.println("overview 조회 실패 (contentId: " + contentId + ") : " + e.getMessage());
        }

        return "";
    }

    /**
     * 장소 상세 소개(DetailIntro) 조회
     */
    public String getDetailIntro(String contentId, int contentTypeId) {
        try {
            String url = UriComponentsBuilder.fromUriString(BASE_URL + "/detailIntro2")
                .queryParam("serviceKey", serviceKey)
                .queryParam("MobileOS", MOBILE_OS)
                .queryParam("MobileApp", MOBILE_APP)
                .queryParam("_type", RESPONSE_TYPE)
                .queryParam("contentId", contentId)
                .queryParam("contentTypeId", contentTypeId)
                .build(true)
                .toUriString();

            String response = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode item = getResponseItem(response);

            if (!item.isArray() || item.isEmpty()) {
                return "";
            }

            JsonNode intro = item.get(0);
            StringJoiner joiner = new StringJoiner(" ");

            add(joiner, "운영시간", intro, "opentime", "usetime", "usetimefood", "usetimeculture", "usetimeleports");
            add(joiner, "휴무일", intro, "restdate");
            add(joiner, "주차", intro, "parking", "parkingfood");
            add(joiner, "대표메뉴", intro, "firstmenu");
            add(joiner, "메뉴", intro, "treatmenu");
            add(joiner, "문의", intro, "infocenter", "infocenterfood");
            add(joiner, "체험안내", intro, "expguide");
            add(joiner, "반려동물", intro, "chkpet");

            return joiner.toString();

        } catch (Exception e) {
            System.out.println("detailIntro 조회 실패 (contentId: " + contentId + ") : " + e.getMessage());
            return "";
        }
    }

    private void add(StringJoiner joiner, String label, JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText("").trim();
            if (!value.isBlank()) {
                joiner.add(label + ": " + value);
                return;
            }
        }
    }

    private JsonNode getResponseItem(String responseJson) throws Exception {
        return objectMapper.readTree(responseJson)
            .path("response")
            .path("body")
            .path("items")
            .path("item");
    }
}