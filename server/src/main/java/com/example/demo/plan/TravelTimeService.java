package com.example.demo.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

// 카카오모빌리티 길찾기(자동차) API로 하루 동선 안에서 연속된 두 장소 사이의
// 실제 이동 시간을 조회한다(이슈 #44). 인증 방식은 SubRegionService(로컬/
// 역지오코딩 API)와 동일한 "KakaoAK {REST API 키}" — 실제 호출로 같은
// kakao.api.key가 그대로 통하는 것을 확인했다(별도 활성화 불필요).
@Service
public class TravelTimeService {

    private static final String DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions";

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    // 하루 동선(ordered) 안에서 연속된 장소 쌍(i, i+1)마다 이동 시간을 조회한다.
    // 구간 수만큼의 호출이 서로 완전히 독립적이므로 반드시 병렬로 호출한다 —
    // 순차로 하면 정거장이 많은 날일수록 Gemini 호출들 위에 지연이 더 쌓인다
    // (이슈 #42에서 이미 지적된 문제와 같은 패턴). 결과 리스트의 크기는
    // ordered.size() - 1이고, i번째 값은 "i번째 장소 → i+1번째 장소" 이동
    // 시간(분)이다. 조회 실패한 구간은 null로 남긴다 — 그 구간만 이동시간
    // 정보 없이 진행되고, 전체 일정 생성 자체는 실패하지 않는다.
    public List<Integer> lookupConsecutiveTravelMinutes(List<ObjectNode> ordered, ExecutorService executor) {
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < ordered.size() - 1; i++) {
            ObjectNode from = ordered.get(i);
            ObjectNode to = ordered.get(i + 1);
            futures.add(CompletableFuture.supplyAsync(
                () -> lookupTravelMinutes(
                    from.get("lat").asDouble(), from.get("lng").asDouble(),
                    to.get("lat").asDouble(), to.get("lng").asDouble()
                ),
                executor
            ));
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    Integer lookupTravelMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            // 카카오모빌리티는 좌표를 "경도,위도"(x,y) 순서로 받는다 — 이 프로젝트
            // 다른 곳(lat, lng)과 순서가 반대라 헷갈리기 쉽다.
            String uri = DIRECTIONS_URL
                + "?origin=" + fromLng + "," + fromLat
                + "&destination=" + toLng + "," + toLat;

            String response = restClient.get()
                .uri(uri)
                .header("Authorization", "KakaoAK " + kakaoApiKey)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode routes = root.path("routes");
            if (!routes.isArray() || routes.isEmpty()) return null;

            JsonNode summary = routes.get(0).path("summary");
            if (!summary.has("duration")) return null;

            int durationSeconds = summary.get("duration").asInt();
            return Math.max(1, durationSeconds / 60);
        } catch (Exception e) {
            System.out.println("[TravelTimeService] 이동시간 조회 실패: " + e.getMessage());
            return null;
        }
    }
}
