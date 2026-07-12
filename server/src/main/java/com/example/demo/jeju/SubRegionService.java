package com.example.demo.jeju;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SubRegionService {

    @Value("${kakao.api.key}")
    private String kakaoApiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestClient restClient = RestClient.create();

    private final JejuPlaceRepository jejuPlaceRepository;

    public SubRegionService(JejuPlaceRepository jejuPlaceRepository) {
        this.jejuPlaceRepository = jejuPlaceRepository;
    }

    /**
     * 좌표 → 읍/면/동(카카오 좌표→행정구역 역지오코딩)으로 sub_region이 비어있는
     * 장소들을 전부 채운다. (기존 region 4대 권역은 그대로 두고 별도 컬럼에 채움)
     */
    public void fillSubRegions() {
        while (true) {
            List<JejuPlace> places = jejuPlaceRepository.findWithoutSubRegion(PageRequest.of(0, 100));
            if (places.isEmpty()) break;

            for (JejuPlace place : places) {
                // 실패해도 반드시 뭔가 써서 처리 완료 처리한다 — 안 그러면 같은 행이
                // findWithoutSubRegion에 계속 잡혀서 무한 루프에 빠진다 (실제로 좌표가
                // 깨진 행 2개에서 겪은 문제).
                String subRegion = "알수없음";
                try {
                    subRegion = lookupSubRegion(place.getLat(), place.getLng());
                    System.out.println("읍면동 매핑 완료 : " + place.getName() + " -> " + subRegion);
                } catch (Exception e) {
                    System.out.println("읍면동 매핑 실패 : " + place.getName());
                    e.printStackTrace();
                } finally {
                    jejuPlaceRepository.updateSubRegion(place.getId(), subRegion);
                }
            }
        }
    }

    private String lookupSubRegion(double lat, double lng) throws Exception {
        String url = "https://dapi.kakao.com/v2/local/geo/coord2regioncode.json"
            + "?x=" + lng
            + "&y=" + lat;

        String response = restClient.get()
            .uri(url)
            .header("Authorization", "KakaoAK " + kakaoApiKey)
            .retrieve()
            .body(String.class);

        JsonNode documents = objectMapper.readTree(response).path("documents");
        if (documents.isEmpty()) return "알수없음";

        String name = documents.get(0).path("region_3depth_name").asText("");
        return name.isBlank() ? "알수없음" : name;
    }
}
