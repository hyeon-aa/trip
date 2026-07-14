package com.example.demo.plan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.demo.jeju.JejuPlace;

class PlaceGroupingServiceTest {

    private final PlaceGroupingService groupingService = new PlaceGroupingService();

    private JejuPlace place(String name, String category, String region, String subRegion) {
        JejuPlace p = new JejuPlace();
        p.setName(name);
        p.setCategory(category);
        p.setRegion(region);
        p.setSubRegion(subRegion);
        return p;
    }

    @Test
    void 장소가_없으면_빈_문자열을_반환한다() {
        String result = groupingService.buildPlacesPrompt(Map.of());

        assertThat(result).isEmpty();
    }

    @Test
    void region_다음_sub_region_순으로_묶고_지정된_권역_순서를_따른다() {
        Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
        placeIdMap.put("p1", place("성산일출봉", "관광지", "동부", "성산읍"));
        placeIdMap.put("p2", place("섭지코지", "관광지", "동부", "성산읍"));
        placeIdMap.put("p3", place("우도", "관광지", "동부", null));
        placeIdMap.put("p4", place("협재해수욕장", "관광지", "서부", "애월읍"));
        placeIdMap.put("p5", place("중문색달해변", "관광지", "남부", ""));

        String result = groupingService.buildPlacesPrompt(placeIdMap);

        String expected = "\n[동부]\n"
            + "  - 성산읍\n"
            + "    [p1] 성산일출봉 (관광지)\n"
            + "    [p2] 섭지코지 (관광지)\n"
            + "  - 기타\n"
            + "    [p3] 우도 (관광지)\n"
            + "\n[서부]\n"
            + "  - 애월읍\n"
            + "    [p4] 협재해수욕장 (관광지)\n"
            + "\n[남부]\n"
            + "  - 기타\n"
            + "    [p5] 중문색달해변 (관광지)\n";
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void 제주시_권역만_있는_장소도_출력된다() {
        Map<String, JejuPlace> placeIdMap = new LinkedHashMap<>();
        placeIdMap.put("p1", place("동문시장", "관광지", "제주시", "일도동"));

        String result = groupingService.buildPlacesPrompt(placeIdMap);

        assertThat(result).isEqualTo("\n[제주시]\n  - 일도동\n    [p1] 동문시장 (관광지)\n");
    }
}
