package com.example.demo.place;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PlaceService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    public String searchPlaces(String query) {
        RestClient restClient = RestClient.create();

        return restClient.get()
        .uri("https://dapi.kakao.com/v2/local/search/keyword.json?query=" + query)
        .header("Authorization", "KakaoAK " + kakaoApiKey)
        .header("KA", "sdk/1.0.0 os/java origin/localhost")  // 이거 추가
        .retrieve()
        .body(String.class);
}
}