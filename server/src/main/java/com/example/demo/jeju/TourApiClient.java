package com.example.demo.jeju;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class TourApiClient {

    @Value("${tourapi.service.key}")
    private String serviceKey;

    private final ObjectMapper objectMapper =
        new ObjectMapper();

    private final RestClient restClient =
        RestClient.create();

    /**
     * 관광지 목록 조회
     */
    public JsonNode getPlaces(
        int contentTypeId,
        int pageNo
    ) throws Exception {

        String url =
            "https://apis.data.go.kr/B551011/KorService2/areaBasedList2"
                + "?serviceKey=" + serviceKey
                + "&MobileOS=ETC"
                + "&MobileApp=Trip"
                + "&_type=json"
                + "&areaCode=39"
                + "&contentTypeId=" + contentTypeId
                + "&numOfRows=100"
                + "&pageNo=" + pageNo;

        String response =
            restClient.get()
                .uri(url)
                .retrieve()
                .body(String.class);

        return objectMapper.readTree(response)
            .path("response")
            .path("body")
            .path("items")
            .path("item");
    }

}