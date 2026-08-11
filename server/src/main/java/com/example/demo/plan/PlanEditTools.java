package com.example.demo.plan;

import java.util.Comparator;
import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.example.demo.jeju.JejuPlace;
import com.example.demo.jeju.JejuPlaceRepository;
import com.example.demo.jeju.JejuPlaceUtil;

// 이슈 #58(일정 편집 시 위치 지정 삽입을 Tool로 정확하게 처리)의 Tool 기반
// 해결책. Gemini API는 Tool 호출과 responseSchema(구조화 출력 강제)를 같은
// 요청에서 동시에 지원하지 않는다(직접 curl로 확인 — "Function calling with
// a response mime type: 'application/json' is unsupported"). 그래서 이 Tool은
// PlanChatController의 기존 구조화 생성 호출과는 별도로, Tool만 켠 1차 호출
// 에서 사용하고, 그 결과를 2차(구조화) 호출의 후보 목록에 병합하는 2단계
// 구조로 쓰일 예정이다.
//
// 기존 편집 규칙(같은 읍/면/동 우선)이 텍스트 패턴 파싱에 의존해 "~ 사이에"
// 같은 표현이 없으면 전혀 안 걸렸던 것과 달리, 이 Tool은 실제 좌표 거리로
// 후보를 직접 계산하므로 표현 방식과 무관하게 동작한다.
@Component
public class PlanEditTools {

    private static final int MAX_RESULTS = 10;

    private final JejuPlaceRepository jejuPlaceRepository;

    public PlanEditTools(JejuPlaceRepository jejuPlaceRepository) {
        this.jejuPlaceRepository = jejuPlaceRepository;
    }

    @Tool(description = "사용자가 언급한 기준 장소(들) 근처의 실제 후보 장소를 좌표 거리 "
        + "기준으로 찾는다. 기준 장소가 두 개면 그 중간 지점 기준으로, 하나면 그 지점 "
        + "기준으로 가까운 순서대로 후보를 반환한다. '~ 사이에', '~ 근처에' 같은 위치 "
        + "지정 요청이 있을 때 반드시 이 도구로 실제 좌표를 확인한 뒤 후보를 골라야 한다 "
        + "— 이름/읍면동만 보고 지리적 근접성을 추측하면 안 된다.")
    public String findNearbyPlaces(
        @ToolParam(description = "기준 장소 1의 이름") String anchorName1,
        @ToolParam(description = "기준 장소 2의 이름 — 기준 장소가 하나뿐이면 생략", required = false)
            String anchorName2,
        @ToolParam(description = "카테고리 힌트(예: 음식점, 카페, 관광지) — 없으면 전체", required = false)
            String category
    ) {
        JejuPlace anchor1 = findFirst(anchorName1);
        if (anchor1 == null) {
            return "기준 장소 \"" + anchorName1 + "\"를 찾을 수 없어요.";
        }

        double centerLat = anchor1.getLat();
        double centerLng = anchor1.getLng();
        StringBuilder header = new StringBuilder("\"" + anchor1.getName() + "\" 기준");

        if (anchorName2 != null && !anchorName2.isBlank()) {
            JejuPlace anchor2 = findFirst(anchorName2);
            if (anchor2 != null) {
                centerLat = (anchor1.getLat() + anchor2.getLat()) / 2;
                centerLng = (anchor1.getLng() + anchor2.getLng()) / 2;
                header = new StringBuilder(
                    "\"" + anchor1.getName() + "\"와(과) \"" + anchor2.getName() + "\" 중간 지점 기준"
                );
            }
        }

        final double lat = centerLat;
        final double lng = centerLng;

        // 카테고리 힌트는 main_category(관광지/음식점/문화시설/레포츠, 굵은 분류)로도
        // 올 수 있고 category(예: "카페", "한식", 세부 분류)로도 올 수 있다 — 이
        // 둘은 서로 다른 필드라(예: 카페는 category="카페", mainCategory="음식점"),
        // mainCategory만 정확히 비교하면 "카페" 같은 힌트가 항상 매칭 실패했다
        // (코드 리뷰에서 지적됨). 둘 다 받아들이도록 OR로 검사한다.
        List<JejuPlace> candidates = jejuPlaceRepository.findAll().stream()
            .filter(p -> p.getLat() != null && p.getLng() != null)
            .filter(p -> category == null || category.isBlank()
                || category.equals(p.getMainCategory())
                || (p.getCategory() != null && p.getCategory().contains(category)))
            .sorted(Comparator.comparingDouble(p ->
                JejuPlaceUtil.haversineMeters(lat, lng, p.getLat(), p.getLng())))
            .limit(MAX_RESULTS)
            .toList();

        if (candidates.isEmpty()) {
            return header + " 조건에 맞는 후보를 찾지 못했어요.";
        }

        StringBuilder sb = new StringBuilder(header + " 가까운 순서로 찾은 실제 후보:\n");
        for (JejuPlace p : candidates) {
            double km = JejuPlaceUtil.haversineMeters(lat, lng, p.getLat(), p.getLng()) / 1000.0;
            sb.append(String.format("- id=p%d, %s (%s, 약 %.1fkm)%n", p.getId(), p.getName(), p.getCategory(), km));
        }
        return sb.toString();
    }

    private JejuPlace findFirst(String name) {
        List<JejuPlace> matches = jejuPlaceRepository.findBestMatchesByName(name);
        return matches.isEmpty() ? null : matches.get(0);
    }
}
