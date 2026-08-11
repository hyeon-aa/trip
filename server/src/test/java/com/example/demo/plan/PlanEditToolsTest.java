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

        when(repository.findByNameContaining("협재해수욕장")).thenReturn(List.of(anchor));
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

        when(repository.findByNameContaining("협재해수욕장")).thenReturn(List.of(a));
        when(repository.findByNameContaining("한담해변")).thenReturn(List.of(b));
        when(repository.findAll()).thenReturn(List.of(a, b, middle, edge));

        String result = tools.findNearbyPlaces("협재해수욕장", "한담해변", null);

        assertThat(result).contains("중간 지점 기준");
        // 중간 지점(126.1)에 가장 가까운 "중간식당"이 "구석카페"보다 먼저 나와야 한다
        assertThat(result.indexOf("중간식당")).isLessThan(result.indexOf("구석카페"));
    }

    @Test
    void 기준_장소를_못_찾으면_후보_조회_없이_바로_안내한다() {
        when(repository.findByNameContaining("존재하지않는곳")).thenReturn(List.of());

        String result = tools.findNearbyPlaces("존재하지않는곳", null, null);

        assertThat(result).contains("찾을 수 없어요");
    }

    @Test
    void 카테고리_힌트가_있으면_해당_카테고리만_후보로_남긴다() {
        JejuPlace anchor = place(1, "협재해수욕장", "관광지", "관광지", 33.0, 126.0);
        JejuPlace restaurant = place(2, "협재식당", "음식점", "음식점", 33.0, 126.001);
        JejuPlace cafe = place(3, "협재카페", "카페", "카페", 33.0, 126.001);

        when(repository.findByNameContaining("협재해수욕장")).thenReturn(List.of(anchor));
        when(repository.findAll()).thenReturn(List.of(anchor, restaurant, cafe));

        String result = tools.findNearbyPlaces("협재해수욕장", null, "음식점");

        assertThat(result).contains("협재식당");
        assertThat(result).doesNotContain("협재카페");
    }
}
