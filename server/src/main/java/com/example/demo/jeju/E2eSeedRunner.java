package com.example.demo.jeju;

import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// Playwright E2E(이슈 #48)에서 벡터 검색/동선 최적화 같은 실제 코드 경로를
// 태우려면 jeju_place에 실제 데이터가 있어야 한다. "e2e" 프로필로 서버를
// 띄울 때만 고정된 소량의 실제 제주 장소를 심는다 — 반복 기동해도 중복
// 삽입되지 않게 이름 존재 여부로 먼저 확인한다.
@Component
@Profile("e2e")
public class E2eSeedRunner implements CommandLineRunner {

    private record SeedPlace(
        String name, String category, String mainCategory,
        String region, String address, double lat, double lng
    ) {
    }

    // KakaoMap.tsx의 지도 기본 중심({lat: 33.450701, lng: 126.570667})과
    // 기본 zoom(level=3) 기준으로, 실측(Playwright 스크린샷+DOM 검사) 결과
    // 화면 스케일바가 "50m" 단위로 나올 만큼 가시 범위가 매우 좁다 — 위/경도
    // 0.001~0.004도(약 111~444m) 정도만 떨어져도 지도 밖으로 밀려나
    // display:none 처리된다. 그래서 오프셋을 훨씬 촘촘하게(0.0002~0.0004도,
    // 약 20~45m) 잡아 전부 한 화면 안에 들어오게 한다 — 실제 추천 품질과
    // 무관한 E2E 전용 합성 좌표이므로 이름도 명확하게 테스트용으로 표시한다.
    private static final double CENTER_LAT = 33.450701;
    private static final double CENTER_LNG = 126.570667;

    private static final List<SeedPlace> SEED_PLACES = List.of(
        new SeedPlace("E2E 테스트 장소 1", "관광지", "관광지", "제주시", "제주 제주시 첨단로", CENTER_LAT + 0.0002, CENTER_LNG + 0.0002),
        new SeedPlace("E2E 테스트 장소 2", "관광지", "관광지", "제주시", "제주 제주시 첨단로", CENTER_LAT - 0.0002, CENTER_LNG + 0.0003),
        new SeedPlace("E2E 테스트 장소 3", "관광지", "관광지", "제주시", "제주 제주시 첨단로", CENTER_LAT + 0.0003, CENTER_LNG - 0.0002),
        new SeedPlace("E2E 테스트 장소 4", "음식점", "음식점", "제주시", "제주 제주시 첨단로", CENTER_LAT - 0.0003, CENTER_LNG - 0.0003),
        new SeedPlace("E2E 테스트 장소 5", "관광지", "관광지", "제주시", "제주 제주시 첨단로", CENTER_LAT + 0.0001, CENTER_LNG - 0.0004),
        new SeedPlace("E2E 테스트 장소 6", "관광지", "관광지", "제주시", "제주 제주시 첨단로", CENTER_LAT - 0.0004, CENTER_LNG + 0.0001)
    );

    private final JejuPlaceRepository jejuPlaceRepository;

    public E2eSeedRunner(JejuPlaceRepository jejuPlaceRepository) {
        this.jejuPlaceRepository = jejuPlaceRepository;
    }

    @Override
    public void run(String... args) {
        for (SeedPlace seed : SEED_PLACES) {
            if (jejuPlaceRepository.existsByName(seed.name())) {
                continue;
            }
            jejuPlaceRepository.insertPlace(
                seed.name(), seed.category(), seed.mainCategory(),
                seed.region(), seed.address(), seed.lat(), seed.lng(),
                "E2E 테스트용 시드 데이터"
            );
            jejuPlaceRepository.findByName(seed.name()).stream()
                .findFirst()
                .ifPresent(p -> jejuPlaceRepository.updateEmbedding(p.getId(), dummyEmbedding()));
        }
        System.out.println("[E2E 시드] jeju_place 테스트 데이터 확인 완료 (" + SEED_PLACES.size() + "개)");
    }

    // pgvector 컬럼(vector(3072))에 CAST가 되려면 차원 수만 맞으면 되고,
    // StubAiChatService가 유사도 계산과 무관하게 응답을 만들어주므로 값 자체는
    // 의미 없다.
    private String dummyEmbedding() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 3072; i++) {
            if (i > 0) sb.append(',');
            sb.append('0');
        }
        return sb.append(']').toString();
    }
}
