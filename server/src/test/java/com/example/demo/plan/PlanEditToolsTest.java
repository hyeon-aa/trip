package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;

class PlanEditToolsTest {

    private final JejuPlaceRepository repository = mock(JejuPlaceRepository.class);
    private final PlanEditTools tools = new PlanEditTools(repository);

    private JejuPlace place(long id, String name, String category, String mainCategory, double lat, double lng) {
        JejuPlace p = new JejuPlace();
        p.setId(id);
        p.setName(name);
        p.setCategory(category);
        p.setMainCategory(mainCategory);
        p.setLat(lat);
        p.setLng(lng);
        return p;
    }

    @Test
    void 기준_장소_하나면_그_지점에서_가까운_순서로_후보를_반환한다() {
        JejuPlace anchor = place(1, "협재해수욕장", "관광지", "관광지", 33.3938, 126.2394);
        JejuPlace near = place(2, "협재정식당", "음식점", "음식점", 33.3940, 126.2400);
        JejuPlace far = place(3, "성산일출봉", "관광지", "관광지", 33.4591, 126.9405);

        when(repository.findBestMatchesByName("협재해수욕장")).thenReturn(List.of(anchor));
        when(repository.findAll()).thenReturn(List.of(anchor, near, far));

        String result = tools.findNearbyPlaces("협재해수욕장", null, null);

        assertThat(result).contains("협재해수욕장").contains("협재정식당");
        // 가까운 후보(near)가 먼 후보(far)보다 먼저 나와야 한다
        assertThat(result.indexOf("협재정식당")).isLessThan(result.indexOf("성산일출봉"));
    }

    @Test
    void 기준_장소가_둘이면_중간_지점_기준으로_찾는다() {
        JejuPlace a = place(1, "협재해수욕장", "관광지", "관광지", 33.0, 126.0);
        JejuPlace b = place(2, "한담해변", "관광지", "관광지", 33.0, 126.2);
        // 중간(126.1)에 정확히 있는 후보
        JejuPlace middle = place(3, "중간식당", "음식점", "음식점", 33.0, 126.1);
        // 한쪽 끝에 붙어있는 후보
        JejuPlace edge = place(4, "구석카페", "카페", "카페", 33.0, 126.001);

        when(repository.findBestMatchesByName("협재해수욕장")).thenReturn(List.of(a));
        when(repository.findBestMatchesByName("한담해변")).thenReturn(List.of(b));
        when(repository.findAll()).thenReturn(List.of(a, b, middle, edge));

        String result = tools.findNearbyPlaces("협재해수욕장", "한담해변", null);

        assertThat(result).contains("중간 지점 기준");
        // 중간 지점(126.1)에 가장 가까운 "중간식당"이 "구석카페"보다 먼저 나와야 한다
        assertThat(result.indexOf("중간식당")).isLessThan(result.indexOf("구석카페"));
    }

    @Test
    void 기준_장소를_못_찾으면_후보_조회_없이_바로_안내한다() {
        when(repository.findBestMatchesByName("존재하지않는곳")).thenReturn(List.of());

        String result = tools.findNearbyPlaces("존재하지않는곳", null, null);

        assertThat(result).contains("찾을 수 없어요");
    }

    @Test
    void 카테고리_힌트가_mainCategory와_일치하면_해당_카테고리만_후보로_남긴다() {
        JejuPlace anchor = place(1, "협재해수욕장", "관광지", "관광지", 33.0, 126.0);
        // 실제 DB 관례: main_category는 관광지/음식점/문화시설/레포츠 4종뿐이고,
        // category는 그 안의 세부 분류(한식/카페 등)다 — 카페도 main_category는
        // "음식점"이다(코드 리뷰에서 지적된 실제 데이터 형태).
        JejuPlace restaurant = place(2, "협재식당", "한식", "음식점", 33.0, 126.001);
        JejuPlace sight = place(3, "협재포구", "포구", "관광지", 33.0, 126.001);

        when(repository.findBestMatchesByName("협재해수욕장")).thenReturn(List.of(anchor));
        when(repository.findAll()).thenReturn(List.of(anchor, restaurant, sight));

        String result = tools.findNearbyPlaces("협재해수욕장", null, "음식점");

        assertThat(result).contains("협재식당");
        assertThat(result).doesNotContain("협재포구");
    }

    @Test
    void 카테고리_힌트가_세부_category와_일치하면_그것도_후보로_남긴다() {
        JejuPlace anchor = place(1, "협재해수욕장", "관광지", "관광지", 33.0, 126.0);
        // "카페"는 main_category가 아니라 category(세부 분류)에만 있다 — main_category만
        // 비교했다면 이 카페는 항상 걸러졌을 것(코드 리뷰에서 발견된 버그).
        JejuPlace cafe = place(2, "협재카페", "카페", "음식점", 33.0, 126.001);
        JejuPlace restaurant = place(3, "협재식당", "한식", "음식점", 33.0, 126.002);

        when(repository.findBestMatchesByName("협재해수욕장")).thenReturn(List.of(anchor));
        when(repository.findAll()).thenReturn(List.of(anchor, cafe, restaurant));

        String result = tools.findNearbyPlaces("협재해수욕장", null, "카페");

        assertThat(result).contains("협재카페");
        assertThat(result).doesNotContain("협재식당");
    }

    @Test
    void 정확히_일치하는_이름이_있으면_그것을_기준으로_삼는다() {
        // findBestMatchesByName은 부분 일치 여러 개 중 정확한 이름을 우선하도록
        // JejuPlaceRepository가 보장한다 — 이 테스트는 PlanEditTools가 그 결과의
        // 첫 번째 항목을 신뢰하고 그대로 쓰는지만 확인한다(정확도 보장 자체는
        // JejuPlaceRepository 쪽 책임).
        JejuPlace exact = place(1, "협재", "관광지", "관광지", 33.0, 126.0);
        JejuPlace near = place(2, "협재정식당", "한식", "음식점", 33.0, 126.001);

        when(repository.findBestMatchesByName("협재")).thenReturn(List.of(exact));
        when(repository.findAll()).thenReturn(List.of(exact, near));

        String result = tools.findNearbyPlaces("협재", null, null);

        assertThat(result).contains("\"협재\" 기준");
    }
}
